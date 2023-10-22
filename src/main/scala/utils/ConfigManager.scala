package org.sripas.seaman
package utils

import streaming.core.settings.Implicits.{KafkaBrokerSettingsGroup, MQTTBrokerSettingsGroup}
import streaming.core.settings.{BrokerSettingsGroup, KafkaSettings, MQTTSettings, MaterializedKafkaSettings, MaterializedMQTTSettings}

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}

object ConfigManager {

  val config: Config = ConfigFactory.load()

  val akkaLogLevel: String = config.getString("akka.loglevel")

  val standaloneMode: Boolean = config.getBoolean("seaman.standalone-mode")
  val restoreChannelsStopped: Boolean = config.getBoolean("seaman.restore-channels-stopped")

  val encryptionAuthSecret: String = config.getString("seaman.encryption.auth_secret")

  val httpPort: Int = config.getInt("seaman.http.port")
  val httpHost: String = config.getString("seaman.http.host")

  val httpHostString: String = s"$httpHost:$httpPort"

  val mongoHost: String = config.getString("mongodb-client.host")
  val mongoPort: Int = config.getInt("mongodb-client.port")
  val mongoDatabase: String = config.getString("mongodb-client.database")
  val mongoUser: String = config.getString("mongodb-client.user")
  val mongoPassword: String = config.getString("mongodb-client.password")

  val maxShutdownDuration: FiniteDuration = FiniteDuration(Duration(config.getString("seaman.max-shutdown-duration")).toSeconds, SECONDS)
  val defaultParallelism: Int = config.getInt("seaman.parallelism")
  val defaultBufferSize: Int = config.getInt("seaman.buffer-size")

  val kafkaHost: String = config.getString("kafka-broker.host")
  val kafkaPort: Int = config.getInt("kafka-broker.port")

  val mqttProtocol: String = config.getString("mqtt-broker.protocol")
  val mqttHost: String = config.getString("mqtt-broker.host")
  val mqttPort: Int = config.getInt("mqtt-broker.port")

  val mqttInputClientId: String = config.getString("mqtt-broker.inputClientId")
  val mqttOutputClientId: String = config.getString("mqtt-broker.outputClientId")
  val mqttErrorClientId: String = config.getString("mqtt-broker.errorClientId")
  val mqttMonitorInputClientId: String = config.getString("mqtt-broker.monitorInputClientId")
  val mqttMonitorOutputClientId: String = config.getString("mqtt-broker.monitorOutputClientId")

  val defaultInputKafkaSettings: KafkaSettings = KafkaSettings(host = kafkaHost, port = kafkaPort, groupId = None)
  val defaultOutputKafkaSettings: KafkaSettings = KafkaSettings(host = kafkaHost, port = kafkaPort, groupId = None)
  val defaultErrorKafkaSettings: KafkaSettings = KafkaSettings(host = kafkaHost, port = kafkaPort, groupId = None)
  val defaultInputMonitorKafkaSettings: KafkaSettings = KafkaSettings(host = kafkaHost, port = kafkaPort, groupId = None)
  val defaultOutputMonitorKafkaSettings: KafkaSettings = KafkaSettings(host = kafkaHost, port = kafkaPort, groupId = None)

  val defaultKafkaSettings: BrokerSettingsGroup[KafkaSettings] = BrokerSettingsGroup[KafkaSettings](
    inputTopicBrokerSettings = defaultInputKafkaSettings,
    outputTopicBrokerSettings = defaultOutputKafkaSettings,
    errorTopicBrokerSettings = defaultErrorKafkaSettings,
    monitorInputTopicBrokerSettings = defaultInputMonitorKafkaSettings,
    monitorOutputTopicBrokerSettings = defaultOutputMonitorKafkaSettings
  )

  def defaultMaterializedKafkaSettings(defaultInputGroupId: String,
                                       defaultOutputGroupId: String,
                                       defaultErrorGroupId: String,
                                       defaultMonitorInputGroupId: String,
                                       defaultMonitorOutputGroupId: String): BrokerSettingsGroup[MaterializedKafkaSettings] =
    defaultKafkaSettings.materialize(
      defaultInputGroupId,
      defaultOutputGroupId,
      defaultErrorGroupId,
      defaultMonitorInputGroupId,
      defaultMonitorOutputGroupId
    )

  val defaultMqttInputSettings: MQTTSettings = MQTTSettings(protocol = mqttProtocol, host = mqttHost, port = mqttPort, clientId = Some(mqttInputClientId), None, None)
  val defaultMqttOutputSettings: MQTTSettings = MQTTSettings(protocol = mqttProtocol, host = mqttHost, port = mqttPort, clientId = Some(mqttOutputClientId), None, None)
  val defaultMqttErrorSettings: MQTTSettings = MQTTSettings(protocol = mqttProtocol, host = mqttHost, port = mqttPort, clientId = Some(mqttErrorClientId), None, None)
  val defaultMqttInputMonitorSettings: MQTTSettings = MQTTSettings(protocol = mqttProtocol, host = mqttHost, port = mqttPort, clientId = Some(mqttMonitorInputClientId), None, None)
  val defaultMqttOutputMonitorSettings: MQTTSettings = MQTTSettings(protocol = mqttProtocol, host = mqttHost, port = mqttPort, clientId = Some(mqttMonitorOutputClientId), None, None)

  val defaultMqttSettings: BrokerSettingsGroup[MQTTSettings] = BrokerSettingsGroup[MQTTSettings](
    inputTopicBrokerSettings = defaultMqttInputSettings,
    outputTopicBrokerSettings = defaultMqttOutputSettings,
    errorTopicBrokerSettings = defaultMqttErrorSettings,
    monitorInputTopicBrokerSettings = defaultMqttInputMonitorSettings,
    monitorOutputTopicBrokerSettings = defaultMqttOutputMonitorSettings
  )

  val defaultMaterializedMqttSettings: BrokerSettingsGroup[MaterializedMQTTSettings] =
    defaultMqttSettings.materialize(
      mqttInputClientId,
      mqttOutputClientId,
      mqttErrorClientId,
      mqttMonitorInputClientId,
      mqttMonitorOutputClientId
    )
}
