package org.sripas.seaman
package rest

import streaming.json.SettingsJsonSupport
import utils.ConfigManager

import akka.http.scaladsl.server.Route
import spray.json.{JsObject, enrichAny}

object SettingsService extends RestService with SettingsJsonSupport {

  override val routes: Route = getSettings

  def getSettings: Route = {
    pathEndOrSingleSlash {
      get {
        complete(JsObject(
          "kafkaSettings" -> ConfigManager.defaultKafkaSettings.toJson,
          "mqttSettings" -> ConfigManager.defaultMqttSettings.toJson,
          "maxShutdownDuration" -> ConfigManager.maxShutdownDuration.toString().toJson,
          "parallelism" -> ConfigManager.defaultParallelism.toJson,
          "bufferSize" -> ConfigManager.defaultBufferSize.toJson,
//          "logLevel" -> ConfigManager.akkaLogLevel.toJson,
          "standaloneMode" -> ConfigManager.standaloneMode.toJson,
          "restoreChannelsStopped" -> ConfigManager.restoreChannelsStopped.toJson,
          "mongoHost" -> ConfigManager.mongoHost.toJson,
          "mongoPort" -> ConfigManager.mongoPort.toJson,
          "mqttClientIdPrefixes" -> JsObject(
            "inputClientId" -> ConfigManager.mqttInputClientId.toJson,
            "outputClientId" -> ConfigManager.mqttOutputClientId.toJson,
            "errorClientId" -> ConfigManager.mqttErrorClientId.toJson,
            "mqttMonitorInputClientId" -> ConfigManager.mqttMonitorInputClientId.toJson,
            "mqttMonitorOutputClientId" -> ConfigManager.mqttMonitorOutputClientId.toJson
          )
        ))
      }
    }
  }

}
