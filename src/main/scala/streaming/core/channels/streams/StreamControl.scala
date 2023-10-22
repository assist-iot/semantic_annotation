package org.sripas.seaman
package streaming.core.channels.streams

import streaming.core.channels.{ChannelStatusInfo, ChannelStatusInfoOptions}
import streaming.core.utils.BrokerType.BrokerType

import akka.Done

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait StreamControl {

  val sourceType: BrokerType

  def shutdown(): Future[Done]

  val completion: Future[Done]

  def isStopped: Boolean = completion.isCompleted

  val topicControls: TopicControls

  private def completionErrorToStringOption: Option[String] = {
    completion.value match {
      case Some(value) => value match {
        case Failure(exception) => Some(exception.toString)
        case Success(_) => None
      }
      case None => None
    }
  }

  def status: ChannelStatusInfo = ChannelStatusInfo(
    isStopped = isStopped,
    inputMonitorTopicEnabled = topicControls.inputMonitor.isEnabled(),
    outputMonitorTopicEnabled = topicControls.outputMonitor.isEnabled(),
    errorTopicEnabled = topicControls.error.isEnabled(),
    error = completionErrorToStringOption
  )

  def statusOptions: ChannelStatusInfoOptions = {
    val topics = List(
      topicControls.inputMonitor,
      topicControls.outputMonitor,
      topicControls.error
    ).map(tc => if (tc.canBeControlled()) Some(tc.isEnabled()) else None)
    ChannelStatusInfoOptions(
      isStopped = Some(isStopped),
      error = completionErrorToStringOption,
      inputMonitorTopicEnabled = topics(0),
      outputMonitorTopicEnabled = topics(1),
      errorTopicEnabled = topics(2),
    )
  }

}
