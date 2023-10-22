package org.sripas.seaman
package control

import rml.CARMLChannelMetadata
import rml.json.RMLJsonSupport
import streaming.core.channels.{ChannelInfo, ChannelManager, ChannelStatusInfoOptions, MaterializedChannel}
import streaming.core.exceptions.WrongSettingsException

import akka.Done
import org.slf4j.{Logger, LoggerFactory}
import streaming.core.utils.encryption.{CipherEncryptor, Encryptor}
import streaming.core.utils.encryption.Implicits._

import spray.json.JsObject

import scala.concurrent.{ExecutionContext, Future}

class StandaloneChannelController[A](
                                      channelManager: ChannelManager[A, CARMLChannelMetadata],
                                      authCipherSecret: String
                                    )(
                                      implicit executionContext: ExecutionContext
                                    )
  extends CARMLChannelController with StatusController with RMLJsonSupport {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val encryptor: Encryptor = CipherEncryptor(authCipherSecret)

  override def initialize: Future[Done] = Future.successful(Done)

  override def initializationResult: Future[InitializationResult] =
    Future.successful(InitializationResults.initialized)

  override def shutdown: Future[Done] = {
    logger.info("Shuting down channels")
    Future.sequence(shutdownChannels()).map(_ => {
      logger.info("Shut down channels")
      Done
    })
  }

  override def getAllChannels: Future[Map[String, MaterializedChannel[CARMLChannelMetadata]]] =
    Future.successful(channelManager.getChannels)

  override def getAllChannelsAsJson(
                                     includeAll: Boolean,
                                     includeStatus: Boolean,
                                     includeMetadata: Boolean,
                                     includeSettings: Boolean,
                                     includeRml: Boolean
                                   ): Future[Map[String, JsObject]] =
    getAllChannels.map(_.map { case (channelId, materializedChannel) =>
      (channelId, materializedChannel.encryptAuthData.serializeToJson(includeAll, includeStatus, includeMetadata, includeSettings, includeRml))
    })

  override def getChannel(
                           channelId: String
                         ): Future[MaterializedChannel[CARMLChannelMetadata]] = Future.successful(
    channelManager.getChannel(channelId)
  )

  override def getChannelAsJson(
                                 channelId: String
                               )(
                                 includeAll: Boolean,
                                 includeStatus: Boolean,
                                 includeMetadata: Boolean,
                                 includeSettings: Boolean,
                                 includeRml: Boolean
                               ): Future[JsObject] =
    getChannel(channelId).map(_.encryptAuthData).map(_.serializeToJson(includeAll, includeStatus, includeMetadata, includeSettings, includeRml))

  override def addChannel(channelInfo: ChannelInfo[CARMLChannelMetadata]): Future[MaterializedChannel[CARMLChannelMetadata]] = {
    channelInfo.metadata.mapping.tryTransformationWithException()

    channelManager.createChannel(
      channelSettings = channelInfo.settings,
      transformation = channelInfo.metadata.transformation,
      channelMetadata = channelInfo.metadata,
      status = channelInfo.status
    )
  }

  override def stopAndRemoveChannel(channelId: String): Future[Done] = channelManager.stopAndRemoveChannel(channelId)

  override def shutdownChannels(): List[Future[Done]] =
    channelManager.stopAndRemoveAllChannels()

  override def restartChannel(channelId: String): Future[MaterializedChannel[CARMLChannelMetadata]] = channelManager.restartChannel(channelId)

  override def updateChannelStatus(channelId: String)(updates: ChannelStatusInfoOptions): Future[ChannelUpdatesMade] = {
    val channel = channelManager.getChannel(channelId)

    // Check for empty settings object
    if (List(updates.isStopped,
      updates.inputMonitorTopicEnabled,
      updates.outputMonitorTopicEnabled,
      updates.errorTopicEnabled
    ).forall(_.isEmpty)) {
      throw new WrongSettingsException("ChannelStatus object was empty, so no updates were made.")
    }

    // Change topic settings and map them to changes made (if any)
    val topicControls = channel.streamControl.topicControls
    val topicChanges = List(
      (updates.inputMonitorTopicEnabled, topicControls.inputMonitor),
      (updates.outputMonitorTopicEnabled, topicControls.outputMonitor),
      (updates.errorTopicEnabled, topicControls.error)
    )
    val topicUpdates = topicChanges.map { case (toggle, control) => toggle match {
      case Some(true) if control.isDisabled() =>
        if (control.enable()) Some(true) else None
      case Some(false) if control.isEnabled() =>
        if (control.disable()) None else Some(false)
      case _ => None
    }
    }

    // Change channel running status, if requested
    val updatesFuture = updates.isStopped match {
      case Some(true) if !channel.streamControl.isStopped =>
        channel.streamControl.shutdown().map(_ => Some(false))
      case Some(false) if channel.streamControl.isStopped =>
        channelManager.restartChannel(channelId).map(_ => Some(true))
      case _ => Future.successful(None)
    }

    // Collect changes made
    val changesMade = updatesFuture.map(channelRunningChanges =>
      ChannelUpdatesMade(
        channelRunning = channelRunningChanges,
        inputMonitorTopicEnabled = topicUpdates(0),
        outputMonitorTopicEnabled = topicUpdates(1),
        errorTopicEnabled = topicUpdates(2)
      ))

    changesMade
  }
}
