package org.sripas.seaman
package streaming.core.utils.encryption

import streaming.core.channels.MaterializedChannel

import streaming.core.settings.{MaterializedChannelSettings, MaterializedMQTTSettings, MaterializedTopicSettings}

object Implicits {
  implicit class MaterializedChannelEncryptor[M](val materializedChannel: MaterializedChannel[M]){
    def encryptAuthData(implicit encryptor: Encryptor): MaterializedChannel[M] =
      materializedChannel.copy(
        settings = materializedChannel.settings.encryptAuthData
      )

    def decryptAuthData(implicit encryptor: Encryptor): MaterializedChannel[M] =
      materializedChannel.copy(
        settings = materializedChannel.settings.decryptAuthData
      )
  }

  implicit class MaterializedChannelSettingsEncryptor(val materializedChannelSettings: MaterializedChannelSettings){
    def encryptAuthData(implicit encryptor: Encryptor): MaterializedChannelSettings =
      materializedChannelSettings.copy(
        inputTopicSettings = materializedChannelSettings.inputTopicSettings.encryptAuthData,
        outputTopicSettings = materializedChannelSettings.outputTopicSettings.encryptAuthData,
        errorTopicSettings = materializedChannelSettings.errorTopicSettings.map(_.encryptAuthData),
        monitoringInputTopicSettings = materializedChannelSettings.monitoringInputTopicSettings.map(_.encryptAuthData),
        monitoringOutputTopicSettings = materializedChannelSettings.monitoringOutputTopicSettings.map(_.encryptAuthData),
      )


    def decryptAuthData(implicit encryptor: Encryptor): MaterializedChannelSettings =
      materializedChannelSettings.copy(
        inputTopicSettings = materializedChannelSettings.inputTopicSettings.decryptAuthData,
        outputTopicSettings = materializedChannelSettings.outputTopicSettings.decryptAuthData,
        errorTopicSettings = materializedChannelSettings.errorTopicSettings.map(_.decryptAuthData),
        monitoringInputTopicSettings = materializedChannelSettings.monitoringInputTopicSettings.map(_.decryptAuthData),
        monitoringOutputTopicSettings = materializedChannelSettings.monitoringOutputTopicSettings.map(_.decryptAuthData),
      )
  }

  implicit class MaterializedTopicSettingsEncryptor(val materializedTopicSettings: MaterializedTopicSettings){
    def encryptAuthData(implicit encryptor: Encryptor): MaterializedTopicSettings =
      materializedTopicSettings.copy(
        mqttSettings = materializedTopicSettings.mqttSettings.encryptAuthData
      )


    def decryptAuthData(implicit encryptor: Encryptor): MaterializedTopicSettings =
      materializedTopicSettings.copy(
        mqttSettings = materializedTopicSettings.mqttSettings.decryptAuthData
      )
  }

  implicit class MaterializedMQTTSettingsEncryptor(val materializedMQTTSettings: MaterializedMQTTSettings){
    def encryptAuthData(implicit encryptor: Encryptor): MaterializedMQTTSettings =
      materializedMQTTSettings.copy(
        user = materializedMQTTSettings.user.map(encryptor.encryptString),
        password = materializedMQTTSettings.password.map(encryptor.encryptString)
      )

    def decryptAuthData(implicit encryptor: Encryptor): MaterializedMQTTSettings =
      materializedMQTTSettings.copy(
        user = materializedMQTTSettings.user.map(encryptor.decryptString),
        password = materializedMQTTSettings.password.map(encryptor.decryptString)
      )
  }

}

