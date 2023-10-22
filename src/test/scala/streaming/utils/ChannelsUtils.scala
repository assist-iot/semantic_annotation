package org.sripas.seaman
package streaming.utils

import streaming.core.channels.{ChannelManager, ChannelStatusInfo, MaterializedChannel}
import streaming.core.settings._
import streaming.core.utils.BrokerType

import akka.actor.testkit.typed.scaladsl.ActorTestKit

import scala.concurrent.{ExecutionContext, Future}

trait ChannelsUtils {

  val actorTestKit = ActorTestKit.apply("SeamanStreamerTests")

  implicit val actorSystem = actorTestKit.system

  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  object DefaultTopics {
    object Kafka {
      val input = "testInput"
      val output = "testOutput"
      val inputMonitor = "testMonitorInput"
      val outputMonitor = "testMonitorOutput"
      val error = "testError"
    }

    object MQTT {
      val input = "test/input"
      val output = "test/output"
      val inputMonitor = "test/monitor/input"
      val outputMonitor = "test/monitor/output"
      val error = "test/error"
    }
  }

  val defaultBasicMQTTSettings = MaterializedMQTTSettings(
    protocol = "tcp",
    host = "127.0.0.1",
    port = 1883,
    clientId = "seamanTest",
    user = None,
    password = None
  )

  def breakDownKafkaBootstrapServers(bootstrapServers: String): Array[(String, Int)] = bootstrapServers.split(",").map(s => {
    val lastColonIdx = s.lastIndexOf(":")
    val firstDoubleslashIndex = s.indexOf("//")
    val (host, portStr) = (s take lastColonIdx drop (firstDoubleslashIndex + 2), s drop (lastColonIdx + 1))
    (host, portStr.toInt)
  })

  // TODO: Prepare overloads for more options and include MQTT settings
  def getChannelManagerInstance[M](kafkaBootstrapServers: String): ChannelManager[Nothing, M] = {
    // TODO: Use testcontainers here
    val (kafkaHost, kafkaPort) = breakDownKafkaBootstrapServers(kafkaBootstrapServers).head
//    val (kafkaHost, kafkaPort) = ("127.0.0.1", 9093)
    val kafkaSettings = KafkaSettings(host = kafkaHost, port = kafkaPort)

    ChannelManager[Nothing, M](
      defaultKafkaSettings = BrokerSettingsGroup[KafkaSettings](
        kafkaSettings,
        kafkaSettings,
        kafkaSettings,
        kafkaSettings,
        kafkaSettings
      ),
      defaultMaterializedMqttSettings = BrokerSettingsGroup[MaterializedMQTTSettings](
        inputTopicBrokerSettings = defaultBasicMQTTSettings.withClientId("seamanTest/input"),
        outputTopicBrokerSettings = defaultBasicMQTTSettings.withClientId("seamanTest/output"),
        monitorInputTopicBrokerSettings = defaultBasicMQTTSettings.withClientId("seamanTest/monitor/input"),
        monitorOutputTopicBrokerSettings = defaultBasicMQTTSettings.withClientId("seamanTest/monitor/output"),
        errorTopicBrokerSettings = defaultBasicMQTTSettings.withClientId("seamanTest/error"),
      ),
      defaultParallelism = 1,
      defaultBufferSize = 5
    )
  }


  def createKafkaOnlyChannelWithDefaultSettings[M](
                                                    channelManager: ChannelManager[Nothing, M],
                                                    transformation: String => String,
                                                    channelMetadata: M
                                                  ): Future[MaterializedChannel[M]] = {
    val channel = channelManager.createChannel(
      channelSettings = ChannelSettings(
        channelId = None,
        inputTopicSettings = TopicSettings(
          topic = DefaultTopics.Kafka.input,
          brokerType = BrokerType.KAFKA,
          kafkaSettings = Some(channelManager.defaultKafkaSettings.inputTopicBrokerSettings),
          mqttSettings = None
        ),
        outputTopicSettings = TopicSettings(
          topic = DefaultTopics.Kafka.output,
          brokerType = BrokerType.KAFKA,
          kafkaSettings = Some(channelManager.defaultKafkaSettings.outputTopicBrokerSettings),
          mqttSettings = None
        ),
        monitorInputTopicSettings = Some(TopicSettings(
          topic = DefaultTopics.Kafka.inputMonitor,
          brokerType = BrokerType.KAFKA,
          kafkaSettings = Some(channelManager.defaultKafkaSettings.monitorInputTopicBrokerSettings),
          mqttSettings = None
        )),
        monitorOutputTopicSettings = Some(TopicSettings(
          topic = DefaultTopics.Kafka.outputMonitor,
          brokerType = BrokerType.KAFKA,
          kafkaSettings = Some(channelManager.defaultKafkaSettings.monitorOutputTopicBrokerSettings),
          mqttSettings = None
        )),
        errorTopicSettings = Some(TopicSettings(
          topic = DefaultTopics.Kafka.output,
          brokerType = BrokerType.KAFKA,
          kafkaSettings = Some(channelManager.defaultKafkaSettings.errorTopicBrokerSettings),
          mqttSettings = None
        )),
        parallelism = Some(channelManager.defaultParallelism),
        bufferSize = Some(channelManager.defaultBufferSize)
      ),
      transformation = transformation,
      channelMetadata = channelMetadata,
      status = ChannelStatusInfo(
        isStopped = false,
        inputMonitorTopicEnabled = true,
        outputMonitorTopicEnabled = true,
        errorTopicEnabled = true,
        error = None
      )
    )
    channel
  }
}
