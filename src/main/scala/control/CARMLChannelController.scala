package org.sripas.seaman
package control

import rml.CARMLChannelMetadata
import streaming.core.channels.{ChannelInfo, ChannelStatusInfoOptions, MaterializedChannel}

import akka.Done
import spray.json.JsObject

import scala.concurrent.Future

trait CARMLChannelController {

  def getAllChannels: Future[Map[String, MaterializedChannel[CARMLChannelMetadata]]]

  def getAllChannelsAsJson(
                            includeAll: Boolean,
                            includeStatus: Boolean,
                            includeMetadata: Boolean,
                            includeSettings: Boolean,
                            includeRml: Boolean
                          ): Future[Map[String, JsObject]]

  def getChannel(channelId: String): Future[MaterializedChannel[CARMLChannelMetadata]]

  def getChannelAsJson(
                        channelId: String
                      )(
                        includeAll: Boolean,
                        includeStatus: Boolean,
                        includeMetadata: Boolean,
                        includeSettings: Boolean,
                        includeRml: Boolean
                      ): Future[JsObject]

  def addChannel(
                  channelInfo: ChannelInfo[CARMLChannelMetadata]
                ): Future[MaterializedChannel[CARMLChannelMetadata]]

  def stopAndRemoveChannel(channelId: String): Future[Done]

  def shutdownChannels(): List[Future[Done]]

  def restartChannel(channelId: String): Future[MaterializedChannel[CARMLChannelMetadata]]

  def updateChannelStatus(channelId: String)(updates: ChannelStatusInfoOptions): Future[ChannelUpdatesMade]


  /**
   * An object informing about changes made to channel status.
   *
   * Each parameter describes changes:
   * None - no changes made
   * Some(true) - changes made, and new status is "true"
   * Some(false) - changes made, and new status is "false"
   *
   * @param channelRunning            Was the channel turned on or off?
   * @param inputMonitorTopicEnabled  Was the input monitoring topic enabled or disabled?
   * @param outputMonitorTopicEnabled Was the output monitoring topic enabled or disabled?
   * @param errorTopicEnabled         Was the error topic enabled or disabled?
   */
  case class ChannelUpdatesMade(
                                 channelRunning: Option[Boolean] = None,
                                 inputMonitorTopicEnabled: Option[Boolean] = None,
                                 outputMonitorTopicEnabled: Option[Boolean] = None,
                                 errorTopicEnabled: Option[Boolean] = None
                               ) {
    val topicsUpdated: Boolean = Seq(inputMonitorTopicEnabled, outputMonitorTopicEnabled, errorTopicEnabled).exists(_.isDefined)

    val channelRunningUpdated: Boolean = channelRunning.isDefined

    val updatesMade: Boolean = topicsUpdated || channelRunningUpdated

    val channelStopped: Boolean = channelRunning match {
      case Some(false) => true
      case _ => false
    }
    val channelStarted: Boolean = channelRunning match {
      case Some(true) => true
      case _ => false
    }

    def toChannelStatusInfoOptions: ChannelStatusInfoOptions = ChannelStatusInfoOptions(
      isStopped = channelRunning.map(!_),
      inputMonitorTopicEnabled = inputMonitorTopicEnabled,
      outputMonitorTopicEnabled = outputMonitorTopicEnabled,
      errorTopicEnabled = errorTopicEnabled
    )
  }

}
