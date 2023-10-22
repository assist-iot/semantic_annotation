package org.sripas.seaman
package streaming.core.settings

import java.util.UUID

case class ChannelSettings(
                            channelId: Option[String] = None,
                            inputTopicSettings: TopicSettings,
                            outputTopicSettings: TopicSettings,
                            monitorInputTopicSettings: Option[TopicSettings] = None,
                            monitorOutputTopicSettings: Option[TopicSettings] = None,
                            errorTopicSettings: Option[TopicSettings] = None,
                            parallelism: Option[Int] = None,
                            bufferSize: Option[Int] = None,
                          ) {
  def materialize(
                      defaultChannelId: String,
                      defaultKafkaSettings: BrokerSettingsGroup[MaterializedKafkaSettings],
                      defaultMqttSettings: BrokerSettingsGroup[MaterializedMQTTSettings],
                      defaultParallelism: Int,
                      defaultBufferSize: Int,
                    ): MaterializedChannelSettings = {
    MaterializedChannelSettings(
      channelId match {
        case Some(x) => x
        case None => defaultChannelId
      },
      inputTopicSettings.materialize(defaultKafkaSettings.inputTopicBrokerSettings, defaultMqttSettings.inputTopicBrokerSettings),
      outputTopicSettings.materialize(defaultKafkaSettings.outputTopicBrokerSettings, defaultMqttSettings.outputTopicBrokerSettings),
      monitorInputTopicSettings.map(_.materialize(defaultKafkaSettings.monitorInputTopicBrokerSettings, defaultMqttSettings.monitorInputTopicBrokerSettings)),
      monitorOutputTopicSettings.map(_.materialize(defaultKafkaSettings.monitorOutputTopicBrokerSettings, defaultMqttSettings.monitorOutputTopicBrokerSettings)),
      errorTopicSettings.map(_.materialize(defaultKafkaSettings.errorTopicBrokerSettings, defaultMqttSettings.errorTopicBrokerSettings)),
      parallelism.getOrElse(defaultParallelism),
      bufferSize.getOrElse(defaultBufferSize)
    )
  }

  /**
   * Materializes these settings and adjusts MQTT client IDs using channel ID
   * @param defaultChannelId
   * @param defaultKafkaSettings
   * @param defaultMqttSettings
   * @return
   */
  def materializeWithChannelId(
                      defaultChannelId: String,
                      defaultKafkaSettings: BrokerSettingsGroup[MaterializedKafkaSettings],
                      defaultMqttSettings: BrokerSettingsGroup[MaterializedMQTTSettings],
                      defaultParallelism: Int,
                      defaultBufferSize: Int
                    ): MaterializedChannelSettings = {
    val newChannelId = channelId match {
      case Some(x) => x
      case None => defaultChannelId
    }
//    val clientIdPrefix = s"${inputTopicSettings.mqttSettings.flatMap(_.clientId).getOrElse(defaultMqttSettings.clientId)}/"
//    val inputClientId = s"${clientIdPrefix}input/$newChannelId"
//    val outputClientId = s"${clientIdPrefix}output/$newChannelId"
//    val errorClientId = s"${clientIdPrefix}error/$newChannelId"
//    val monitorInputClientId = s"${clientIdPrefix}monitor/input/$newChannelId"
//    val monitorOutputClientId = s"${clientIdPrefix}monitor/output/$newChannelId"

    // TODO: Maybe un-spaghetti this

    val inputClientId = getClientID(inputTopicSettings.mqttSettings, defaultMqttSettings.inputTopicBrokerSettings,Some(newChannelId))
    val outputClientId = getClientID(outputTopicSettings.mqttSettings, defaultMqttSettings.outputTopicBrokerSettings,Some(newChannelId))

    val errorClientId = getClientID(errorTopicSettings.flatMap(_.mqttSettings), defaultMqttSettings.errorTopicBrokerSettings,Some(newChannelId))
    val monitorInputClientId = getClientID(monitorInputTopicSettings.flatMap(_.mqttSettings), defaultMqttSettings.monitorInputTopicBrokerSettings,Some(newChannelId))
    val monitorOutputClientId = getClientID(monitorOutputTopicSettings.flatMap(_.mqttSettings), defaultMqttSettings.monitorOutputTopicBrokerSettings,Some(newChannelId))

    MaterializedChannelSettings(
      newChannelId,
      inputTopicSettings.materializeWithClientId(defaultKafkaSettings.inputTopicBrokerSettings, defaultMqttSettings.inputTopicBrokerSettings, inputClientId),
      outputTopicSettings.materializeWithClientId(defaultKafkaSettings.outputTopicBrokerSettings, defaultMqttSettings.outputTopicBrokerSettings, outputClientId),
      monitorInputTopicSettings.map(_.materializeWithClientId(defaultKafkaSettings.monitorInputTopicBrokerSettings, defaultMqttSettings.monitorInputTopicBrokerSettings, errorClientId)),
      monitorOutputTopicSettings.map(_.materializeWithClientId(defaultKafkaSettings.monitorOutputTopicBrokerSettings, defaultMqttSettings.monitorOutputTopicBrokerSettings, monitorInputClientId)),
      errorTopicSettings.map(_.materializeWithClientId(defaultKafkaSettings.errorTopicBrokerSettings, defaultMqttSettings.errorTopicBrokerSettings, monitorOutputClientId)),
      parallelism.getOrElse(defaultParallelism),
      bufferSize.getOrElse(defaultBufferSize)
    )
  }

  private def getClientID(mqttSettings: Option[MQTTSettings], defaultMQTTSettings: MaterializedMQTTSettings, channelId: Option[String]): String = {
    val maxLength = 23 // According to MQTT 3.1.1 clientIDs between 1 and 23 chars must be accepted
    val baseClientId = mqttSettings.flatMap(_.clientId).getOrElse(defaultMQTTSettings.clientId)
    val uuid = UUID.randomUUID().toString.replaceAll("-", "")
    val clientIdPostfix = channelId match {
      case Some(x) => "/" + x
      case _ => ""
    }
    s"$baseClientId$clientIdPostfix$uuid".takeRight(maxLength)
  }
}

case class MaterializedChannelSettings(
                                        channelId: String,
                                        inputTopicSettings: MaterializedTopicSettings,
                                        outputTopicSettings: MaterializedTopicSettings,
                                        monitoringInputTopicSettings: Option[MaterializedTopicSettings],
                                        monitoringOutputTopicSettings: Option[MaterializedTopicSettings],
                                        errorTopicSettings: Option[MaterializedTopicSettings],
                                        parallelism: Int,
                                        bufferSize: Int,
                                      )
