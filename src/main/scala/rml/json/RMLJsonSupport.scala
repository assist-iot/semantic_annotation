package org.sripas.seaman
package rml.json

import rml.InputFormat.InputFormat
import rml.OutputFormat.OutputFormat
import rml._
import streaming.core.channels.MaterializedChannel
import streaming.json.{ChannelsJsonSupport, MaterializedChannelDeserializer, SettingsJsonSupport}

import spray.json.{JsObject, JsString, JsValue, JsonFormat, RootJsonFormat, enrichAny}

trait RMLJsonSupport extends ChannelsJsonSupport with SettingsJsonSupport {

  implicit val inputFormatFormat: JsonFormat[InputFormat] = InputFormatFormat
  implicit val outputFormatFormat: JsonFormat[OutputFormat] = OutputFormatFormat

  implicit val rmlMappingMetadataFormat: RootJsonFormat[RMLMapping] = RMLMappingFormat
  implicit val carmlChannelMetadataFormat: JsonFormat[CARMLChannelMetadata] = CARMLChannelMetadataFormat

  implicit val rmlMappingHeaderFormat: JsonFormat[RMLMappingHeader] = jsonFormat8(RMLMappingHeader)
  implicit val carmlChannelMetadataHeaderFormat: JsonFormat[CARMLChannelMetadataHeader] = jsonFormat4(CARMLChannelMetadataHeader)

  //  implicit def carmlMaterializedChannelFormat: JsonFormat[MaterializedChannel[CARMLChannelMetadata]] = CarmlMaterializedChannelFormat

  implicit class MaterializedCARMLChannelExtension(materializedChannel: MaterializedChannel[CARMLChannelMetadata]) {

    /**
     * Serializes a MaterializedChannel[CARMLChannelMetadata] to JSON. Parameters decide, what data is included in the returned JSON object.
     *
     * @param includeAll Include everything, regardless of other parameters.
     * @param includeStatus Include "status" key with serialized {@link ChannelStatusInfo}.
     * @param includeMetadata Include "metadata" key with serialized {@link CARMLChannelMetadata}.
     *                        Inclusion of RML code in the metadata is controlled with the {@param includeRml} param.
     * @param includeSettings Include "settings" key with serialized {@link MaterializedChannelSettings}.
     * @param includeRml Include RML in the serialized metadata. If {@param includeMetadata} is false, this param has no effect.
     * @return A JsObject with keys and values containing serialized representation of a MaterializedChannel[CARMLChannelMetadata].
     *         If all parameters are false, an empty object is returned.
     */
    def serializeToJson(includeAll: Boolean = false,
                        includeStatus: Boolean = false,
                        includeMetadata: Boolean = false,
                        includeSettings: Boolean = false,
                        includeRml: Boolean = false
                       ): JsObject = {
      val statusEntry: Map[String, JsValue] = if (includeAll || includeStatus) {
        Map("status" -> materializedChannel.streamControl.statusOptions.toJson)
      } else Map()
      val metadataEntry: Map[String, JsValue] = if (includeAll || includeMetadata) {
        // TODO: Should includeRml without includeMetadata be allowed?
        val rmlMaybeHeaderOnly = if (includeAll || includeRml) {
          materializedChannel.metadata.toJson
        } else {
          materializedChannel.metadata.asHeader.toJson
        }
        Map("metadata" -> rmlMaybeHeaderOnly)
      } else Map()
      val settingsEntry: Map[String, JsValue] = if (includeAll || includeSettings) {
        Map("settings" -> materializedChannel.settings.toJson)
      } else Map()

      JsObject(statusEntry ++ metadataEntry ++ settingsEntry)
    }
  }

  private object InputFormatFormat extends JsonFormat[InputFormat] {
    override def write(obj: InputFormat): JsValue = JsString(obj.toString)

    override def read(json: JsValue): InputFormat = InputFormat.withName(json.convertTo[String])
  }

  private object OutputFormatFormat extends JsonFormat[OutputFormat] {
    override def write(obj: OutputFormat): JsValue = JsString(obj.toString)

    override def read(json: JsValue): OutputFormat = OutputFormat.withName(json.convertTo[String])
  }

  //  private object CarmlMaterializedChannelFormat extends JsonFormat[MaterializedChannel[CARMLChannelMetadata]] {
  //
  //  }

  private object RMLMappingFormat extends RootJsonFormat[RMLMapping] {
    override def read(json: JsValue): RMLMapping = {
      val fields = json.asJsObject.fields
      RMLMapping(
        name = fields.get("name").map(_.convertTo[String]),
        author = fields.get("author").map(_.convertTo[String]),
        version = fields.get("version").map(_.convertTo[String]),
        description = fields.get("description").map(_.convertTo[String]),
        inputFormat = fields("inputFormat").convertTo[InputFormat],
        outputFormat = fields("outputFormat").convertTo[OutputFormat],
        tags = fields.get("tags").map(_.convertTo[Set[String]]).getOrElse(Set()),
        rml = fields("rml").convertTo[String],
      )
    }

    override def write(obj: RMLMapping): JsValue = {
      val optionals = Map(
        "name" -> obj.name,
        "author" -> obj.author,
        "version" -> obj.version,
        "description" -> obj.description)
        .filter { case (str, maybeString) => maybeString.isDefined }
        .map { case (key, value) => (key, value.get.toJson) }

      val set = if (obj.tags.isEmpty) Map() else {
        Map(
          "tags" -> obj.tags.toJson
        )
      }

      val rest = Map("inputFormat" -> obj.inputFormat.toJson,
        "outputFormat" -> obj.outputFormat.toJson,
        "rml" -> obj.rml.toJson,
        "rmlHash" -> obj.rmlHash.toJson)

      JsObject(optionals ++ rest ++ set)
    }
  }

  private object CARMLChannelMetadataFormat extends JsonFormat[CARMLChannelMetadata] {
    override def write(obj: CARMLChannelMetadata): JsValue = {
      val optionals = Map(
        "name" -> obj.name,
        "author" -> obj.author)
        .filter { case (str, maybeString) => maybeString.isDefined }
        .map { case (key, value) => (key, value.get.toJson) }

      val tags = if (obj.tags.isEmpty) Map() else {
        Map(
          "tags" -> obj.tags.toJson
        )
      }

      val rest = Map(
        "mapping" -> obj.mapping.toJson
      )

      JsObject(optionals ++ tags ++ rest)

    }

    override def read(json: JsValue): CARMLChannelMetadata = {
      val fields = json.asJsObject.fields

      CARMLChannelMetadata(
        name = fields.get("name").map(_.convertTo[String]),
        author = fields.get("author").map(_.convertTo[String]),
        tags = fields.get("tags").map(_.convertTo[Set[String]]).getOrElse(Set()),
        mapping = fields("mapping").convertTo[RMLMapping]
      )
    }
  }
}

object MaterializedCARMLChannelDeserializer extends RMLJsonSupport {

  /**
   * Deserializes a JSON object to a MaterializedChannel object, that represents the JSON object, but does not
   * contain active channel controls. It is therefore not a deserialization into an active channel, that can be controlled.
   *
   * To create an active channel, use a channel manager.
   *
   * @param jsObject
   * @param jsonReader
   * @tparam M type of channel metadata
   * @return
   */
  def deserializeToInactiveObject(jsObject: JsObject): MaterializedChannel[CARMLChannelMetadata] = {
    val channelWithoutTransformation = MaterializedChannelDeserializer.deserializeToInactiveObject[CARMLChannelMetadata](jsObject)

    MaterializedChannel[CARMLChannelMetadata](
      settings = channelWithoutTransformation.settings,
      streamControl = channelWithoutTransformation.streamControl,
      transformation = channelWithoutTransformation.metadata.transformation,
      metadata = channelWithoutTransformation.metadata
    )
  }

}
