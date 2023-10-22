package org.sripas.seaman
package streaming.json

import streaming.core.channels.streams.DisabledStreamControl
import streaming.core.channels.{ChannelInfo, ChannelStatusInfo, ChannelStatusInfoOptions, MaterializedChannel}
import streaming.core.settings.MaterializedChannelSettings

import spray.json.{DefaultJsonProtocol, JsBoolean, JsObject, JsString, JsValue, JsonFormat, JsonReader, RootJsonFormat}

trait ChannelsJsonSupport extends DefaultJsonProtocol with SettingsJsonSupport {

  implicit val channelStatusInfoOptionsFormat: RootJsonFormat[ChannelStatusInfoOptions] = jsonFormat5(ChannelStatusInfoOptions)

  //  implicit val channelStatusInfoFormat: RootJsonFormat[ChannelStatusInfo] = jsonFormat4(ChannelStatusInfo)
  implicit val channelStatusInfoFormat: JsonFormat[ChannelStatusInfo] = ChannelStatusInfoFormat


  implicit def channelInfoFormat[M](implicit metadataFormat: JsonFormat[M]): RootJsonFormat[ChannelInfo[M]] = jsonFormat3(ChannelInfo[M])

  private object ChannelStatusInfoFormat extends JsonFormat[ChannelStatusInfo] {
    override def read(json: JsValue): ChannelStatusInfo = {
      val fields = json.asJsObject.fields
      val defaults = ChannelStatusInfo()
      ChannelStatusInfo(
        isStopped = fields.get("isStopped").map(_.convertTo[Boolean]).getOrElse(defaults.isStopped),
        inputMonitorTopicEnabled = fields.get("inputMonitoringTopicEnabled").map(_.convertTo[Boolean]).getOrElse(defaults.inputMonitorTopicEnabled),
        outputMonitorTopicEnabled = fields.get("outputMonitoringTopicEnabled").map(_.convertTo[Boolean]).getOrElse(defaults.outputMonitorTopicEnabled),
        errorTopicEnabled = fields.get("errorTopicEnabled").map(_.convertTo[Boolean]).getOrElse(defaults.errorTopicEnabled),
        error = fields.get("error").map(_.convertTo[String])
      )
    }

    override def write(obj: ChannelStatusInfo): JsValue = {
      val fields = Map(
        "isStopped" -> JsBoolean(obj.isStopped),
        "inputMonitoringTopicEnabled" -> JsBoolean(obj.inputMonitorTopicEnabled),
        "outputMonitoringTopicEnabled" -> JsBoolean(obj.outputMonitorTopicEnabled),
        "errorTopicEnabled" -> JsBoolean(obj.errorTopicEnabled),
      )
      val optionFields = if (obj.error.isDefined) Map("error" -> JsString(obj.error.get)) else Map()
      JsObject(fields ++ optionFields)
    }
  }
}

object MaterializedChannelDeserializer extends ChannelsJsonSupport {

  /**
   * Deserializes a JSON object to a MaterializedChannel object, that represents the JSON object, but does not
   * contain active channel controls, or an actual transformation. It is therefore not a deserialization into an
   * active channel, that can be controlled.
   *
   * To create an active channel, use a channel manager.
   *
   * WARNING: note, that the transformation is not serialized or deserialized to and from JSON by this method.
   *
   * @param jsObject
   * @param jsonReader
   * @tparam M type of channel metadata
   * @return
   */
  def deserializeToInactiveObject[M](jsObject: JsObject)(implicit jsonReader: JsonReader[M]): MaterializedChannel[M] = {

    val jsFields = jsObject.fields

    val settings = jsFields("settings").convertTo[MaterializedChannelSettings]

    val status = if (jsFields.contains("status")) jsFields("status").convertTo[ChannelStatusInfoOptions] else ChannelStatusInfoOptions()

    val streamControl = DisabledStreamControl(
      brokerType = settings.inputTopicSettings.brokerType,
      inputMonitorTopicEnabled = status.inputMonitorTopicEnabled.getOrElse(false),
      outputMonitorTopicEnabled = status.outputMonitorTopicEnabled.getOrElse(false),
      errorTopicEnabled = status.errorTopicEnabled.getOrElse(false),
      fakeIsStopped = status.isStopped.getOrElse(true)
    )

    val metadata = jsFields("metadata").convertTo[M]

    MaterializedChannel[M](
      settings = settings,
      streamControl = streamControl,
      transformation = (x: String) => x,
      metadata = metadata
    )

  }
}
