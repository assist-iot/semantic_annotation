package org.sripas.seaman
package streaming.core.settings

import streaming.core.utils.BrokerType.BrokerType

case class TopicSettings(
                          topic: String,
                          brokerType: BrokerType,
                          kafkaSettings: Option[KafkaSettings] = None,
                          mqttSettings: Option[MQTTSettings] = None,
                        ) {
  def withDefaults(defaultKafkaSettings: KafkaSettings, defaultMqttSettings: MQTTSettings): TopicSettings = {
    val newKafkaSettings = kafkaSettings match {
      case Some(_) => kafkaSettings
      case None => Some(defaultKafkaSettings)
    }
    val newMQTTSettings = mqttSettings match {
      case Some(_) => mqttSettings
      case None => Some(defaultMqttSettings)
    }
    TopicSettings(topic, brokerType, newKafkaSettings, newMQTTSettings)
  }

  def materialize(defaultKafkaSettings: MaterializedKafkaSettings, defaultMqttSettings: MaterializedMQTTSettings): MaterializedTopicSettings = {
    MaterializedTopicSettings(
      topic,
      brokerType,
      kafkaSettings match {
        case Some(x) => x.materialize(defaultKafkaSettings.groupId)
        case None => defaultKafkaSettings
      },
      mqttSettings match {
        case Some(x) => x.materialize(defaultMqttSettings.clientId)
        case None => defaultMqttSettings
      }
    )
  }

  def materializeWithClientId(defaultKafkaSettings: MaterializedKafkaSettings, defaultMqttSettings: MaterializedMQTTSettings, clientId: String): MaterializedTopicSettings = {
    val newMQTTSettings = mqttSettings match {
      case Some(x) => x.withClientId(clientId).materialize(defaultMqttSettings.clientId)
      case None => defaultMqttSettings.withClientId(clientId)
    }
    MaterializedTopicSettings(
      topic,
      brokerType,
      kafkaSettings match {
        case Some(x) => x.materialize(defaultKafkaSettings.groupId)
        case None => defaultKafkaSettings
      },
      newMQTTSettings
    )
  }
}

case class MaterializedTopicSettings(
                                      topic: String,
                                      brokerType: BrokerType,
                                      kafkaSettings: MaterializedKafkaSettings,
                                      mqttSettings: MaterializedMQTTSettings
                                    )