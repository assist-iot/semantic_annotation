package org.sripas.seaman
package streaming.core.settings

object Implicits {
  implicit class KafkaBrokerSettingsGroup(val brokerSettingsGroup: BrokerSettingsGroup[KafkaSettings]) {
    def materialize(
                     defaultInputGroupId: String,
                     defaultOutputGroupId: String,
                     defaultErrorGroupId: String,
                     defaultMonitorInputGroupId: String,
                     defaultMonitorOutputGroupId: String,
                   ): BrokerSettingsGroup[MaterializedKafkaSettings] = BrokerSettingsGroup[MaterializedKafkaSettings](
      inputTopicBrokerSettings = brokerSettingsGroup.inputTopicBrokerSettings.materialize(defaultInputGroupId),
      outputTopicBrokerSettings = brokerSettingsGroup.outputTopicBrokerSettings.materialize(defaultOutputGroupId),
      errorTopicBrokerSettings = brokerSettingsGroup.errorTopicBrokerSettings.materialize(defaultErrorGroupId),
      monitorInputTopicBrokerSettings = brokerSettingsGroup.monitorInputTopicBrokerSettings.materialize(defaultMonitorInputGroupId),
      monitorOutputTopicBrokerSettings = brokerSettingsGroup.monitorOutputTopicBrokerSettings.materialize(defaultMonitorOutputGroupId)
    )
  }

  implicit class MQTTBrokerSettingsGroup(val brokerSettingsGroup: BrokerSettingsGroup[MQTTSettings]) {
    def materialize(
                     defaultInputClientId: String,
                     defaultOutputClientId: String,
                     defaultErrorClientId: String,
                     defaultMonitorInputClientId: String,
                     defaultMonitorOutputClientId: String,
                   ): BrokerSettingsGroup[MaterializedMQTTSettings] = BrokerSettingsGroup[MaterializedMQTTSettings](
      inputTopicBrokerSettings = brokerSettingsGroup.inputTopicBrokerSettings.materialize(defaultInputClientId),
      outputTopicBrokerSettings = brokerSettingsGroup.outputTopicBrokerSettings.materialize(defaultOutputClientId),
      errorTopicBrokerSettings = brokerSettingsGroup.errorTopicBrokerSettings.materialize(defaultErrorClientId),
      monitorInputTopicBrokerSettings = brokerSettingsGroup.monitorInputTopicBrokerSettings.materialize(defaultMonitorInputClientId),
      monitorOutputTopicBrokerSettings = brokerSettingsGroup.monitorOutputTopicBrokerSettings.materialize(defaultMonitorOutputClientId)
    )
  }
}
