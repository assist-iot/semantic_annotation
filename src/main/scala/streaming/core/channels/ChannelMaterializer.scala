package org.sripas.seaman
package streaming.core.channels

import streaming.core.channels.streams._
import streaming.core.settings.MaterializedChannelSettings
import streaming.core.utils.BrokerType
import streaming.core.settings.{MaterializedKafkaSettings, MaterializedMQTTSettings}

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.kafka._
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.alpakka.mqtt.scaladsl.{MqttMessageWithAck, MqttSink, MqttSource}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Flow, Keep, RestartSource, Sink, Source}
import akka.stream.{KillSwitches, OverflowStrategy, RestartSettings, UniqueKillSwitch}
import akka.util.ByteString
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{IntegerDeserializer, StringDeserializer, StringSerializer}
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory

import java.util.Calendar
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object ChannelMaterializer {

  private val logger = LoggerFactory.getLogger(ChannelMaterializer.getClass)

  class ExtendedGraphStage[B](stage: Source[String, B]) {

    // TODO: Add withXXXStage (regardless of broker type)

    def withMQTTInputMonitorStage(settings: MaterializedChannelSettings) =
      ChannelMaterializer.addMQTTMonitorStage(stage, settings, isInputMonitoringStage = true)

    def withMQTTOutputMonitorStage(settings: MaterializedChannelSettings) =
      ChannelMaterializer.addMQTTMonitorStage(stage, settings, isInputMonitoringStage = false)

    //    def withMQTTMessageProcessingStage[A](settings: MaterializedChannelSettings, transformation: String => String)(implicit actorSystem: ActorSystem[A]) =
    //      ChannelMaterializer.addMessageProcessingStage(stage, settings, transformation)

    //    def withMQTTErrorAndLiftStage(settings: MaterializedChannelSettings) =
    //      ChannelMaterializer.addMQTTErrorAndLiftStage(sourceStage, settings)
    //
    //    def withMQTTOutputStage(settings: MaterializedChannelSettings) =
    //      ChannelMaterializer.addMQTTOutputStage(sourceStage, settings)

  }

  implicit def extendSourceStage[B](sourceStage: Source[String, B]) = new ExtendedGraphStage(sourceStage)

  // TODO: Refactor to extract common graph stages

  def getBasicMQTTSettings(mqttSettings: MaterializedMQTTSettings) = {
    val basicSettings = MqttConnectionSettings(
      mqttSettings.broker,
      mqttSettings.clientId,
      new MemoryPersistence)
      .withAutomaticReconnect(true)
      .withCleanSession(false)
    // Add username and password to connection string, if they exist
    (mqttSettings.user, mqttSettings.password) match {
      case (None, None) => basicSettings
      case _ => basicSettings.withAuth(mqttSettings.user.getOrElse(""), mqttSettings.password.getOrElse(""))
    }
  }

  def getBasicKafkaProducerSettings[A](kafkaSettings: MaterializedKafkaSettings)(implicit actorSystem: ActorSystem[A]): ProducerSettings[String, String] =
    ProducerSettings(actorSystem.toClassic, new StringSerializer, new StringSerializer)
      .withBootstrapServers(kafkaSettings.bootstrapServers)

  def getBasicKafkaSink[A](kafkaSettings: MaterializedKafkaSettings)(implicit actorSystem: ActorSystem[A]): Sink[ProducerRecord[String, String], Future[Done]] = {
    val kafkaProducerSettings = getBasicKafkaProducerSettings(kafkaSettings)
    Producer.plainSink(kafkaProducerSettings)
  }

  def getBasicMQTTSink(mqttSettings: MaterializedMQTTSettings, bufferSize: Int) = {
    val mqttSinkConnectionSettings = getBasicMQTTSettings(mqttSettings)
      .withOfflinePersistenceSettings(
        bufferSize = bufferSize,
        deleteOldestMessage = true,
        persistBuffer = false
      )

    MqttSink(
      mqttSinkConnectionSettings,
      MqttQoS.atLeastOnce)
  }

  def getMQTTSourceStage(settings: MaterializedChannelSettings) = {
    val inputSettings = settings.inputTopicSettings
    val inputTopic = inputSettings.topic
    val mqttSettings = inputSettings.mqttSettings

    val mqttSourceConnectionSettings = getBasicMQTTSettings(mqttSettings)
      .withConnectionTimeout(10.seconds)

    val subscriptions = MqttSubscriptions(inputTopic, MqttQoS.atLeastOnce)
    val mqttSource = MqttSource.atLeastOnce(
      mqttSourceConnectionSettings,
      subscriptions,
      settings.bufferSize)
    val restartingMqttSource = wrapWithAsRestartSource(mqttSource)

    restartingMqttSource
      .viaMat(KillSwitches.single)(Keep.both)
    // TODO: Cannot convert to string here, because the Future always fails and takes the whole stream with it... Try to find out why?
  }

  def getKafkaSourceStage[A](settings: MaterializedChannelSettings)(implicit actorSystem: ActorSystem[A]) = {
    val inputSettings = settings.inputTopicSettings
    val inputTopic = inputSettings.topic
    val kafkaSettings = inputSettings.kafkaSettings

    val kafkaConsumerSettings: ConsumerSettings[Integer, String] =
      ConsumerSettings(actorSystem.toClassic, new IntegerDeserializer, new StringDeserializer)
        .withBootstrapServers(kafkaSettings.bootstrapServers)
        //      .withGroupId(defaultKafkaGroupId)
        //        .withProperty("dual.commit.enabled", "false")
        //    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
        .withConnectionChecker(ConnectionCheckerSettings(
          checkInterval = 3000.milliseconds,
          maxRetries = 5,
          factor = 0.9
        ))
        .withStopTimeout(2.seconds)
        .withGroupId(kafkaSettings.groupId)

    val comSource = Consumer
      .plainSource(kafkaConsumerSettings, Subscriptions.topics(inputTopic))
      .viaMat(KillSwitches.single)(Keep.both)
      //      .committableSource(kafkaConsumerSettings, Subscriptions.topics(inputTopic))
      .buffer(settings.bufferSize, OverflowStrategy.backpressure)
    //      .alsoTo(
    //        Flow.fromFunction[Any, Any](x => s"Got $x").to(Sink.foreach(println))
    //      )

    comSource

    //TODO: Convert payload to string here?
  }

  def getSourceStage[A](settings: MaterializedChannelSettings)(implicit actorSystem: ActorSystem[A]) =
    settings.inputTopicSettings.brokerType match {
      case BrokerType.MQTT => ChannelMaterializer.getMQTTSourceStage(settings)
      case BrokerType.KAFKA => ChannelMaterializer.getKafkaSourceStage(settings)
    }

  def addMQTTMonitorStage[A, B](sourceStage: Source[A, B], settings: MaterializedChannelSettings, isInputMonitoringStage: Boolean) = {
    val monitorSettings = if (isInputMonitoringStage) settings.monitoringInputTopicSettings else settings.monitoringOutputTopicSettings

    val monitorStage = if (monitorSettings.isEmpty) {
      (sourceStage, DisabledTopicControl())
    } else {
      val monitorTopic = monitorSettings.get.topic
      val mqttSettings = monitorSettings.get.mqttSettings

      val mqttSinkMonitorInput = getBasicMQTTSink(mqttSettings, settings.bufferSize)

      val topicControl = BasicTopicControl()

      val stage = sourceStage.alsoTo(
        Flow.apply.divertTo(
          Flow.fromFunction[Any, MqttMessage](
            // TODO: Use Prometheus monitoring format
            _ => MqttMessage(monitorTopic, ByteString(s"@${settings.inputTopicSettings.topic}:${Calendar.getInstance.getTimeInMillis.toString}"))
          ).to(mqttSinkMonitorInput),
          (_: Any) => topicControl.isEnabled()
        ).to(Sink.ignore)
      )

      (stage, topicControl)
    }

    monitorStage
  }

  def addKafkaMonitorStage[A, X, Y](sourceStage: Source[X, Y], settings: MaterializedChannelSettings, isInputMonitoringStage: Boolean)(implicit actorSystem: ActorSystem[A]) = {
    val monitorSettings = if (isInputMonitoringStage) settings.monitoringInputTopicSettings else settings.monitoringOutputTopicSettings

    val monitorStage = if (monitorSettings.isEmpty) {
      (sourceStage, DisabledTopicControl())
    } else {
      val monitorTopic = monitorSettings.get.topic
      val kafkaSettings = monitorSettings.get.kafkaSettings

      val kafkaSink = getBasicKafkaSink(kafkaSettings)

      val topicControl = BasicTopicControl()

      val stage = sourceStage.alsoTo(
        Flow.apply.divertTo(
          Flow.fromFunction[Any, ProducerRecord[String, String]](
            // TODO: Use Prometheus monitoring format
            _ =>
              // TODO: Maybe add RecordHeaders with some metadata
              new ProducerRecord(
                monitorTopic,
                settings.channelId,
                s"@${settings.inputTopicSettings.topic}:${Calendar.getInstance.getTimeInMillis.toString}")
          ).to(kafkaSink),
          (_: Any) => topicControl.isEnabled()
        ).to(Sink.ignore)
      )

      (stage, topicControl)
    }

    monitorStage
  }

  def addMonitorStage[A, X, Y](sourceStage: Source[X, Y], settings: MaterializedChannelSettings, isInputMonitoringStage: Boolean)(implicit actorSystem: ActorSystem[A]) = {
    val topicSettings = if (isInputMonitoringStage) settings.monitoringInputTopicSettings else settings.monitoringOutputTopicSettings
    topicSettings match {
      case Some(ts) => ts.brokerType match {
        case BrokerType.MQTT => addMQTTMonitorStage(sourceStage, settings, isInputMonitoringStage)
        case BrokerType.KAFKA => addKafkaMonitorStage(sourceStage, settings, isInputMonitoringStage)
      }
      case None => (sourceStage, DisabledTopicControl())
    }
  }


  def addMQTTMessageProcessingStage[A, X, Y](stage: Source[X, Y], settings: MaterializedChannelSettings, transformation: String => String)(implicit actorSystem: ActorSystem[A]) = {
    implicit val executionContext = actorSystem.executionContext
    stage.mapAsync(settings.parallelism)(
      mqttMessageWithAck => {
        val msg = mqttMessageWithAck.asInstanceOf[MqttMessageWithAck]
        msg.ack().map(_ => Try(transformation(msg.message.payload.utf8String)))
      }
      //      strPayload => Future {      Try(transformation(strPayload))    }
    )
  }

  def addKafkaMessageProcessingStage[A, X, Y](stage: Source[X, Y], settings: MaterializedChannelSettings, transformation: String => String)(implicit actorSystem: ActorSystem[A]) = {
    implicit val executionContext = actorSystem.executionContext
    stage.mapAsync(settings.parallelism)(
      consumerRecord => Future {
        val rec = consumerRecord.asInstanceOf[ConsumerRecord[Integer, String]]
        Try(transformation(rec.value()))
      }
      //      strPayload => Future {      Try(transformation(strPayload))    }
    )
  }

  def addMessageProcessingStage[A, X, Y](stage: Source[X, Y], settings: MaterializedChannelSettings, transformation: String => String)(implicit actorSystem: ActorSystem[A]) = {
    settings.inputTopicSettings.brokerType match {
      case BrokerType.MQTT => addMQTTMessageProcessingStage(stage, settings, transformation)
      case BrokerType.KAFKA => addKafkaMessageProcessingStage(stage, settings, transformation)
    }
  }

  def addGenericErrorLogStage[Y](stage: Source[Try[String], Y], settings: MaterializedChannelSettings) = {
    val channelId = settings.channelId
    stage.divertToMat(
      Flow.apply
        .map((errorTry: Try[String]) => logger.error(s"ERROR on channel $channelId: $errorTry"))
        .to(Sink.ignore),
      _.isFailure
    )(Keep.left)
  }

  def addMQTTErrorAndLiftStage[Y](stage: Source[Try[String], Y], settings: MaterializedChannelSettings) = {
    val errorSettings = settings.errorTopicSettings

    val (divertStage, errorTopicControl) = if (errorSettings.isEmpty) {
      (addGenericErrorLogStage(stage, settings), DisabledTopicControl())
    } else {
      val mqttSettings = errorSettings.get.mqttSettings
      val mqttErrorSink = getBasicMQTTSink(mqttSettings, settings.bufferSize)
      val topicControl = BasicTopicControl()

      val channelId = settings.channelId
      // [Try[String],Y]
      val errorStage = stage.divertToMat(
        Flow.apply.divertToMat(
          Flow.fromFunction[Try[String], MqttMessage](
            errorTry => {
              logger.error(s"ERROR on channel $channelId: $errorTry")
              // TODO: Use prometheus error logging format
              MqttMessage(errorSettings.get.topic, ByteString(s"$errorTry"))
            }
          ).to(mqttErrorSink),
          (_: Try[String]) => topicControl.isEnabled()
        )(Keep.left)
          .toMat(Sink.ignore)(Keep.left),
        _.isFailure
      )(Keep.left)

      (errorStage, topicControl)
    }

    (divertStage.map(_.get), errorTopicControl)
  }

  def addKafkaErrorAndLiftStage[A, Y](stage: Source[Try[String], Y], settings: MaterializedChannelSettings)(implicit actorSystem: ActorSystem[A]) = {
    val errorSettings = settings.errorTopicSettings

    val (divertStage, errorTopicControl) = if (errorSettings.isEmpty) {
      (addGenericErrorLogStage(stage, settings), DisabledTopicControl())
    } else {
      val errorTopic = errorSettings.get.topic
      val kafkaSettings = errorSettings.get.kafkaSettings
      val kafkaSink = getBasicKafkaSink(kafkaSettings)
      val topicControl = BasicTopicControl()

      val channelId = settings.channelId

      val errorStage = stage.divertToMat(
        Flow.apply.divertToMat(
          Flow.fromFunction[Try[String], ProducerRecord[String, String]](
            errorTry => {
              logger.error(s"ERROR on channel $channelId: $errorTry")
              // TODO: Use Prometheus monitoring format
              // TODO: Maybe add RecordHeaders with some metadata
              new ProducerRecord(
                errorTopic,
                settings.channelId,
                s"$errorTry")
            }
          ).to(kafkaSink),
          (_: Try[String]) => topicControl.isEnabled()
        )(Keep.left)
          .toMat(Sink.ignore)(Keep.left),
        _.isFailure
      )(Keep.left)

      (errorStage, topicControl)
    }

    (divertStage.map(_.get), errorTopicControl)
  }

  def addErrorAndLiftStage[A, Y](stage: Source[Try[String], Y], settings: MaterializedChannelSettings)(implicit actorSystem: ActorSystem[A]) = {
    val errorSettings = settings.errorTopicSettings

    val (divertStage, topicControl) = if (errorSettings.isEmpty) {
      (addGenericErrorLogStage(stage, settings).map(_.get), DisabledTopicControl())
    } else {
      errorSettings.get.brokerType match {
        case BrokerType.MQTT => addMQTTErrorAndLiftStage(stage, settings)
        case BrokerType.KAFKA => addKafkaErrorAndLiftStage(stage, settings)
      }
    }

    (divertStage, topicControl)
  }

  def addMQTTOutputStage[B](stage: Source[String, B], settings: MaterializedChannelSettings) = {
    val mqttSettings = settings.outputTopicSettings.mqttSettings

    val mqttSink = getBasicMQTTSink(mqttSettings, settings.bufferSize)

    val outputStage = stage.map(strPayload => MqttMessage(settings.outputTopicSettings.topic, ByteString(strPayload)))
      .toMat(mqttSink)(Keep.both)

    outputStage
  }

  def addKafkaOutputStage[A, Y](stage: Source[String, Y], settings: MaterializedChannelSettings)(implicit actorSystem: ActorSystem[A]) = {
    val outputTopic = settings.outputTopicSettings.topic
    val kafkaSettings = settings.outputTopicSettings.kafkaSettings

    val kafkaSink = getBasicKafkaSink(kafkaSettings)

    val outputStage = stage
      .map(strPayload =>
        // TODO: Maybe add RecordHeaders with some metadata
        new ProducerRecord(
          outputTopic,
          settings.channelId,
          strPayload
        ))
      .toMat(kafkaSink)(Keep.both)

    outputStage
  }

  def addOutputStage[A, Y](stage: Source[String, Y], settings: MaterializedChannelSettings)(implicit actorSystem: ActorSystem[A]) = {
    settings.outputTopicSettings.brokerType match {
      case BrokerType.MQTT => addMQTTOutputStage(stage, settings)
      case BrokerType.KAFKA => addKafkaOutputStage(stage, settings)
    }
  }

  def getStreamControl[A, X](
                              materializedStream: (X, Future[Done]),
                              settings: MaterializedChannelSettings,
                              topicControls: TopicControls
                            )(
                              implicit actorSystem: ActorSystem[A]
                            ) = {
    settings.inputTopicSettings.brokerType match {
      case BrokerType.MQTT =>
        val ((subscriptionInit: Future[Done], listener: UniqueKillSwitch), streamCompletion: Future[Done]) = materializedStream
        MQTTSourceStreamControl(
          subscriptionInit,
          listener,
          streamCompletion,
          topicControls
        )
      case BrokerType.KAFKA =>
        val ((consumerControl: Consumer.Control, listener: UniqueKillSwitch), streamCompletion: Future[Done]) = materializedStream
        KafkaSourceStreamControl(
          consumerControl,
          listener,
          streamCompletion,
          topicControls
        )
    }
  }

  /**
   * Materializes (creates and starts) a channel with given settings.
   *
   * @param materializedChannelSettings Channel settings that will be used to configure input and output topics, and
   *                                    optionally input and output monitoring and error topics
   * @param transformation Function applied to message payload between input and output stage
   * @param enableInputMonitoringTopic If true, input monitoring topic will be enabled, provided, that it is configured.
   *                                   If the channel settings do not have a configuration for this topic, this parameter
   *                                   will have no effect.
   * @param enableOutputMonitoringTopic If true, output monitoring topic will be enabled, provided, that it is configured.
   *                                    If the channel settings do not have a configuration for this topic, this parameter
   *                                    will have no effect.
   * @param enableErrorTopic If true, error topic will be enabled, provided, that it is configured.
   *                         If the channel settings do not have a configuration for this topic, this parameter
   *                         will have no effect.
   * @param actorSystem
   * @tparam A
   * @return A stream control object, that can be used to control the materialized channel.
   */
  def materializeChannel[A](
                             materializedChannelSettings: MaterializedChannelSettings,
                             transformation: String => String,
                             status: ChannelStatusInfo
                           )(
                             implicit actorSystem: ActorSystem[A]
                           ) = {
    implicit val executionContext: ExecutionContext = actorSystem.executionContext
    Future {
      val sourceStage = getSourceStage(materializedChannelSettings)
      val (withInputMonitor, inputMonitorTopicControl) = addMonitorStage(sourceStage, materializedChannelSettings, isInputMonitoringStage = true)
      val withProcessing = addMessageProcessingStage(withInputMonitor, materializedChannelSettings, transformation)
      // TODO: Investigate, why return type changes from (Object, KillSwitch) to Mat here:
      //      addGenericErrorLogStage(withProcessing).map(_.get)
      //      val xwithErrorAndLift = addMQTTErrorAndLiftStage[(Object, UniqueKillSwitch)](withProcessing, materializedChannelSettings)

      val (withErrorAndLift, errorTopicControl) = addErrorAndLiftStage[A, (Object, UniqueKillSwitch)](withProcessing, materializedChannelSettings)
      val (withOutputMonitor, outputMonitorTopicControl) = addMonitorStage(withErrorAndLift, materializedChannelSettings, isInputMonitoringStage = false)
      val withOutputStage = addOutputStage(withOutputMonitor, materializedChannelSettings)

      // Set initial values for topic controls
      if (status.inputMonitorTopicEnabled) inputMonitorTopicControl.enable() else inputMonitorTopicControl.disable()
      if (status.outputMonitorTopicEnabled) outputMonitorTopicControl.enable() else outputMonitorTopicControl.disable()
      if (status.errorTopicEnabled) errorTopicControl.enable() else errorTopicControl.disable()

      val materializedStream = withOutputStage.run()
      val streamControl = getStreamControl(
        materializedStream,
        materializedChannelSettings,
        streams.TopicControls(
          inputMonitor = inputMonitorTopicControl,
          outputMonitor = outputMonitorTopicControl,
          error = errorTopicControl
        )
      )

      streamControl
    }
  }

  def wrapWithAsRestartSource[M](source: => Source[M, Future[Done]]): Source[M, Future[Done]] = {
    val fut = Promise[Done]

    RestartSource.withBackoff(RestartSettings(200.millis, 5.seconds, randomFactor = 0.2d).withMaxRestarts(5, 10.seconds)) {
      () => source.mapMaterializedValue(mat => fut.completeWith(mat))
    }.mapMaterializedValue(_ => fut.future)
  }

}
