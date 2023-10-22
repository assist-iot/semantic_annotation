package org.sripas.seaman
package streaming

import streaming.core.channels.MaterializedChannel
import streaming.utils.ChannelsUtils

import akka.Done
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Sink, Source}
import com.dimafeng.testcontainers.{ForAllTestContainer, KafkaContainer}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class KafkaOnlyChannels
  extends AnyFlatSpec
    with should.Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    //    with TestContainersForAll
    with ForAllTestContainer
    with ChannelsUtils {

  override val container: KafkaContainer = KafkaContainer()

  //  override type Containers =
  //  override val container: MultipleContainers = MultipleContainers(kafkaContainer, mqttContainer)

  lazy val kafkaBootstrapServers = container.bootstrapServers

  lazy val channelManager = getChannelManagerInstance[Unit](kafkaBootstrapServers)

  lazy val unityChannel: Future[MaterializedChannel[Unit]] = createKafkaOnlyChannelWithDefaultSettings(
    channelManager,
    (x: String) => x,
    ()
  )

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(45, Seconds), interval = Span(5, Millis))

  override def afterAll() = {
    whenReady(unityChannel){ materializedChannel =>
      Await.result(channelManager.stopAndRemoveChannel(materializedChannel.settings.channelId), 10 seconds)
    }
  }

  "Unity transformation Kafka-only channel" should "be successfully created and removed" in {
    val channel = createKafkaOnlyChannelWithDefaultSettings(
      channelManager,
      (x: String) => x,
      ()
    )
    whenReady(channel) { materializedChannel =>

      Thread.sleep(5_000)

      assert(materializedChannel.streamControl.isStopped == false)
      assert(materializedChannel.streamControl.status.error == None)

      whenReady(channelManager.stopAndRemoveChannel(materializedChannel.settings.channelId)) { done =>
        assert(done == Done)
      }
    }
  }

  it should "correctly consume and produce correct messages on all topics" in {
    whenReady(unityChannel)(materializedChannel => {
      // TODO: Finish this
      val input = Seq("one", "two", "integer", "4", "", "YOLO")

      // TODO: read from output topic, not input topic
      val outputControl = Consumer.plainSource(
        ConsumerSettings(actorSystem.toClassic, new StringDeserializer, new StringDeserializer)
          .withBootstrapServers(materializedChannel.settings.outputTopicSettings.kafkaSettings.bootstrapServers)
          .withGroupId(materializedChannel.settings.outputTopicSettings.kafkaSettings.groupId + "test")
          //          .withGroupId(UUID.randomUUID().toString)
          .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
          .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
          .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
        Subscriptions.topics(materializedChannel.settings.outputTopicSettings.topic)
        //        Subscriptions.assignmentWithOffset(
        //          new TopicPartition(
        //            materializedChannel.settings.inputTopicSettings.topic,
        //            0
        //          ) -> 100
        //        )
      )
        .map(record => record.value())
        .toMat(Sink.seq)(DrainingControl.apply)
        .run()

      // TODO: Without this stupid waiting hack, the consumer cannot find the group coordinator fast enough and does not receive messages...
      // TODO: There is also the problem with automatic offsets...
      Await.result(Future {Thread.sleep(3000)}, 21 seconds)

      val kafkaProducerSettings = ProducerSettings(actorSystem.toClassic, new StringSerializer, new StringSerializer)
        .withBootstrapServers(materializedChannel.settings.inputTopicSettings.kafkaSettings.bootstrapServers)

      val inputSource = Source(input).map(payload => new ProducerRecord[String, String](
        materializedChannel.settings.inputTopicSettings.topic,
        payload
      ))
        .runWith(Producer.plainSink(kafkaProducerSettings))

      whenReady(inputSource) { done =>
        // TODO: HACK: Because of automatic offsets, a new Kafka group with "earliest" auto offset reset will receive previous messages as well, if there are any
        // TODO: HACK: "latest" auto offset reset will not produce any messages...
        assert(outputControl.drainAndShutdown().futureValue.takeRight(input.length) == input)
      }
    })
  }

}
