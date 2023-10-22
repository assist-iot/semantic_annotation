package org.sripas.seaman
package streaming.core.channels.streams

import org.sripas.seaman.streaming.core.utils.BrokerType.BrokerType

import akka.Done
import akka.stream.UniqueKillSwitch
import org.sripas.seaman.streaming.core.utils.BrokerType

import scala.concurrent.Future

class MQTTSourceStreamControl(
                               val subscriptionInitialized: Future[Done],
                               val killSwitch: UniqueKillSwitch,
                               val streamCompletion: Future[Done],
                               override val topicControls: TopicControls
                             ) extends StreamControl {

  override val sourceType: BrokerType = BrokerType.MQTT

  override def shutdown(): Future[Done] = isStopped match {
    case true => Future.successful(Done)
    case false =>
      killSwitch.shutdown()
      completion
  }

  override val completion: Future[Done] = streamCompletion
}

object MQTTSourceStreamControl {
  def apply(
             subscriptionInitialized: Future[Done],
             killSwitch: UniqueKillSwitch,
             streamCompletion: Future[Done],
             topicControls: TopicControls
           ) = new MQTTSourceStreamControl(
    subscriptionInitialized,
    killSwitch,
    streamCompletion,
    topicControls
  )
}
