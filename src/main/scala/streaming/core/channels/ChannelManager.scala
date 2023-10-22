package org.sripas.seaman
package streaming.core.channels

import streaming.core.channels.streams.DisabledStreamControl
import streaming.core.exceptions.{NoSuchChannelException, NonUniqueChannelIdException}
import streaming.core.settings.Implicits.KafkaBrokerSettingsGroup
import streaming.core.settings._

import akka.Done
import akka.actor.typed.ActorSystem
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ChannelManager[A, M](
                            val defaultKafkaSettings: BrokerSettingsGroup[KafkaSettings],
                            val defaultMaterializedMqttSettings: BrokerSettingsGroup[MaterializedMQTTSettings],
                            val defaultParallelism: Int,
                            val defaultBufferSize: Int,
                          )(implicit actorSystem: ActorSystem[A]) {
  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  private val channels = scala.collection.concurrent.TrieMap[String, MaterializedChannel[M]]()

  private val logger = LoggerFactory.getLogger(ChannelManager.getClass)

  // TODO: Extra feature: restart policy for channels - if a channel failed, because of monitoring or error topics (non-essential), then
  // try to restart with that topic disabled. If all topics are disabled, and channel still fails, stop trying to restart.

  /**
   * Creates a new channel and adds it to this manager.
   *
   * @param channelSettings Channel settings, that will be materialized before creating the channel.
   * @param transformation Transformation, that this channel will apply to data.
   * @param channelMetadata Metadata associated with this channel.
   * @param status Initial status of this channel. Values of this object decide, if the channel will be started or only created.
   * @return A new materialized channel
   */
  def createChannel(
                             channelSettings: ChannelSettings,
                             transformation: String => String,
                             channelMetadata: M,
                             status: ChannelStatusInfo = ChannelStatusInfo()
                           ): Future[MaterializedChannel[M]] = {

    // TODO: Maybe move generation of IDs to ChannelSettings or KafkaSettings
    val channelId = ChannelManager.createChannelId(
      channelSettings.inputTopicSettings,
      channelSettings.outputTopicSettings)

    val kafkaGroupId = channelSettings
      .inputTopicSettings
      .kafkaSettings
      .flatMap(ks => ks.groupId)
      .getOrElse(ChannelManager.generateRandomKafkaGroupId(channelId))

    val materializedChannelSettings = channelSettings.materializeWithChannelId(
      channelId,
      this.defaultKafkaSettings.materialize(kafkaGroupId,kafkaGroupId,kafkaGroupId,kafkaGroupId,kafkaGroupId),
      this.defaultMaterializedMqttSettings,
      defaultParallelism,
      defaultBufferSize
    )

    createChannelFromMaterializedSettings(
      materializedChannelSettings,
      transformation,
      channelMetadata,
      status)
  }

  /**
   * Creates a new channel and adds it to this manager.
   *
   * @param channelSettings Channel settings, that will be materialized before creating the channel.
   * @param transformation Transformation, that this channel will apply to data.
   * @param channelMetadata Metadata associated with this channel.
   * @param status Initial status of this channel. Values of this object decide, if the channel will be started or only created.
   * @return A new materialized channel
   */
  def createChannelFromMaterializedSettings(
                                                     channelSettings: MaterializedChannelSettings,
                                                     transformation: String => String,
                                                     channelMetadata: M,
                                                     status: ChannelStatusInfo = ChannelStatusInfo()
                                                   ): Future[MaterializedChannel[M]] = {
    if (getChannelOption(channelSettings.channelId).isDefined) {
      Future.failed(
        new NonUniqueChannelIdException(s"Cannot create new channel with given ID. Found existing channel with ID ${channelSettings.channelId}")
      )
    } else {
      logger.debug(s"Creating channel with: \n\tsettings: $channelSettings\n\tmetadata: $channelMetadata\n\tstatus: $status")

      val streamControlFuture = if(status.isStopped) {
        // TODO: Test this
        // If the channel is added with stopped status, do not materialize it, and use mock stream control
        Future {
          DisabledStreamControl(channelSettings.inputTopicSettings.brokerType)
        }
      } else {
        ChannelMaterializer.materializeChannel(
          channelSettings,
          transformation,
          status
        )
      }

      val channel = streamControlFuture.map(streamControl => MaterializedChannel(
        settings = channelSettings,
        streamControl = streamControl,
        transformation = transformation,
        metadata = channelMetadata
      ))
        .map(materializedChannel => {
          //          channels.put(materializedChannel.settings.channelId, materializedChannel)
          // TODO: How to recover, if another thread materialized a channel with the same ID in the meantime?
          channels.put(materializedChannel.settings.channelId, materializedChannel)
          materializedChannel
        })

      channel
    }
  }

  // TODO: Add addChannel(...) (add without starting)

  def getChannelOption(channelId: String): Option[MaterializedChannel[M]] = channels.get(channelId)

  def getChannel(channelId: String): MaterializedChannel[M] = channels.get(channelId) match {
    case Some(value) => value
    case None => throw new NoSuchChannelException(s"No channel with ID: $channelId")
  }

  def getChannels: Map[String, MaterializedChannel[M]] = channels.toMap

  def getChannelsIDs: Set[String] = channels.keys.toSet

  def stopChannel(channelId: String): Future[Done] = {
    logger.debug(s"Stopping channel with ID: $channelId")
    channels.get(channelId) match {
      case Some(materializedChannel) =>
        materializedChannel.streamControl.shutdown()
      case None => Future.failed(
        new NoSuchChannelException(s"Cannot stop channel. No channel with ID $channelId")
      )
    }
  }

  def stopAllChannels(): Iterable[Future[Done]] = {
    logger.debug(s"Stopping all channels")
    channels.values.map(materializedChannel => materializedChannel.streamControl.shutdown())
    // TODO: Decide, if this method should maybe return a single Future that collects all failed futures (or a single Done)

    //    Future.sequence(
    //      channels.values.map(materializedChannel => materializedChannel.streamControl.shutdown())
    //    ).map(_ => Done.done())
  }

  def stopAndRemoveAllChannels(): List[Future[Done]] = {
    logger.debug(s"Stopping and removing all channels")
    val channelsIds = channels.keys.toList
    channelsIds.map(channelId => stopAndRemoveChannel(channelId))
    // TODO: Decide, if this method should maybe return a single Future that collects all failed futures (or a single Done)

    //    Future.sequence(channelsIds.map(channelId =>
    //      stopAndRemoveChannel(channelId)
    //    )).map(_ => Done.done())
  }

  // TODO: Preserve monitor/error topics enable/disable state when restarting a channel

  def restartChannel(channelId: String): Future[MaterializedChannel[M]] = {
    logger.debug(s"Restarting channel with ID: $channelId")
    getChannelOption(channelId) match {
      case Some(channel) =>
        stopAndRemoveChannel(channel.settings.channelId).flatMap(_ =>
          createChannelFromMaterializedSettings(
            channelSettings = channel.settings,
            transformation = channel.transformation,
            channelMetadata = channel.metadata,
            status = ChannelStatusInfo(
              isStopped = false,
              inputMonitorTopicEnabled = channel.streamControl.topicControls.inputMonitor.isEnabled(),
              outputMonitorTopicEnabled = channel.streamControl.topicControls.outputMonitor.isEnabled(),
              errorTopicEnabled = channel.streamControl.topicControls.error.isEnabled()
            )
          )
        )
      case None => Future.failed(
        new NoSuchChannelException(s"Cannot restart channel. No channel with ID $channelId")
      )
    }
  }

  def stopAndRemoveChannel(channelId: String): Future[Done] = {
    logger.debug(s"Stopping and removing channel with ID: $channelId")
    stopChannel(channelId).map(done => {
      channels.remove(channelId)
      done
    })
  }
}

object ChannelManager {
  def apply[A, M](defaultKafkaSettings: BrokerSettingsGroup[KafkaSettings], defaultMaterializedMqttSettings: BrokerSettingsGroup[MaterializedMQTTSettings], defaultParallelism: Int, defaultBufferSize: Int)(implicit actorSystem: ActorSystem[A]) =
    new ChannelManager[A, M](
      defaultKafkaSettings = defaultKafkaSettings,
      defaultMaterializedMqttSettings = defaultMaterializedMqttSettings,
      defaultParallelism = defaultParallelism,
      defaultBufferSize = defaultBufferSize
    )

  /**
   * Creates a random unique ID from topic IDs and random UUID.
   * The generated ID contains input and output topic names (with whitespaces removed),
   * input and output topic broker types, and a random UUID.
   *
   * @param inputTopicSettings  topic settings, that contain the input topic name and broker type
   * @param outputTopicSettings topic settings, that contain the output topic name and broker type
   * @return generated channel ID
   */
  def createChannelId(inputTopicSettings: TopicSettings, outputTopicSettings: TopicSettings): String =
    s"${inputTopicSettings.topic.replaceAll("\\s", "")}" + "_" +
      s"${outputTopicSettings.topic.replaceAll("\\s", "")}" + "_" +
      s"${inputTopicSettings.brokerType.toString.replaceAll("\\s", "")}" + "_" +
      s"${inputTopicSettings.brokerType.toString.replaceAll("\\s", "")}" + "_" +
      s"${UUID.randomUUID().toString}"

  def generateRandomKafkaGroupId(channelId: String): String = {
    val maxLength = 255 // Maximum length of group ID according to Kafka documentation
    "group_" + channelId + UUID.randomUUID().toString.replaceAll("-", "").takeRight(maxLength)
  }
}
