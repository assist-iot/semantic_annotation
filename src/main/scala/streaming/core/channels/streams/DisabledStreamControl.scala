package org.sripas.seaman
package streaming.core.channels.streams

import streaming.core.utils.BrokerType.BrokerType

import akka.Done

import scala.concurrent.Future

class DisabledStreamControl(
                             brokerType: BrokerType,
                             inputMonitorTopicEnabled: Boolean,
                             outputMonitorTopicEnabled: Boolean,
                             errorTopicEnabled: Boolean,
                             fakeIsStopped: Boolean = true
                           ) extends StreamControl {
  override val sourceType: BrokerType = brokerType

  override def shutdown(): Future[Done] = Future.successful(Done)

  override def isStopped: Boolean = fakeIsStopped

  override val completion: Future[Done] = Future.successful(Done)
  override val topicControls: TopicControls = TopicControls(
    inputMonitor = DisabledTopicControl(inputMonitorTopicEnabled),
    outputMonitor = DisabledTopicControl(outputMonitorTopicEnabled),
    error = DisabledTopicControl(errorTopicEnabled)
  )
}

object DisabledStreamControl {
  def apply(
             brokerType: BrokerType,
             inputMonitorTopicEnabled: Boolean = false,
             outputMonitorTopicEnabled: Boolean = false,
             errorTopicEnabled: Boolean = false,
             fakeIsStopped: Boolean = true
           )
  = new DisabledStreamControl(
    brokerType,
    inputMonitorTopicEnabled,
    outputMonitorTopicEnabled,
    errorTopicEnabled,
    fakeIsStopped)
}
