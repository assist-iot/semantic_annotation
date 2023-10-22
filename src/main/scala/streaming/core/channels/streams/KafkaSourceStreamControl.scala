package org.sripas.seaman
package streaming.core.channels.streams

import org.sripas.seaman.streaming.core.utils.BrokerType.BrokerType

import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.stream.UniqueKillSwitch
import org.sripas.seaman.streaming.core.utils.BrokerType

import scala.concurrent.{ExecutionContext, Future}

class KafkaSourceStreamControl[A](
                                   val consumerControl: Consumer.Control,
                                   val killSwitch: UniqueKillSwitch,
                                   val streamCompletion: Future[Done],
                                   override val topicControls: TopicControls
                                 )(
                                   implicit actorSystem: ActorSystem[A]
                                 ) extends StreamControl {
  private implicit val executionContext: ExecutionContext = actorSystem.executionContext

  override val sourceType: BrokerType = BrokerType.KAFKA

  override def shutdown(): Future[Done] = isStopped match {
    case true => Future.successful(Done)
    case false => consumerControl.drainAndShutdown(completion)
  }

  override val completion: Future[Done] = streamCompletion
}

object KafkaSourceStreamControl {
  def apply[A](consumerControl: Consumer.Control,
               killSwitch: UniqueKillSwitch,
               streamCompletion: Future[Done],
               topicControls: TopicControls
              )(
                implicit actorSystem: ActorSystem[A]
              ) = new KafkaSourceStreamControl(
    consumerControl,
    killSwitch,
    streamCompletion,
    topicControls
  )
}