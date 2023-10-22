package org.sripas.seaman
package streaming.json

import streaming.core.settings._

import spray.json.JsonFormat

trait SettingsJsonSupport extends UtilsJsonSupport {

  implicit val kafkaSettingsFormat: JsonFormat[KafkaSettings] = jsonFormat3(KafkaSettings)
  implicit val materializedKafkaSettingsFormat: JsonFormat[MaterializedKafkaSettings] = jsonFormat3(MaterializedKafkaSettings)

  implicit val mqttSettingsFormat: JsonFormat[MQTTSettings] = jsonFormat6(MQTTSettings)
  implicit val materializedMqttSettingsFormat: JsonFormat[MaterializedMQTTSettings] = jsonFormat6(MaterializedMQTTSettings)

  implicit val topicSettingsFormat: JsonFormat[TopicSettings] = jsonFormat4(TopicSettings)
  implicit val materializedTopicSettingsFormat: JsonFormat[MaterializedTopicSettings] = jsonFormat4(MaterializedTopicSettings)

  implicit val channelSettingsFormat: JsonFormat[ChannelSettings] = jsonFormat8(ChannelSettings)
  implicit val MaterializedChannelSettingsFormat: JsonFormat[MaterializedChannelSettings] = jsonFormat8(MaterializedChannelSettings)

  implicit def brokerSettingsGroupFormat[B](implicit format: JsonFormat[B]): JsonFormat[BrokerSettingsGroup[B]] = jsonFormat5(BrokerSettingsGroup[B])

}
