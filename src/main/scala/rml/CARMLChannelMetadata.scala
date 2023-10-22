package org.sripas.seaman
package rml

import streaming.core.utils.Transformer

case class CARMLChannelMetadata(
                            name: Option[String] = None,
                            author: Option[String] = None,
                            tags: Set[String] = Set(),
                            mapping: RMLMapping
                          ) extends Transformer {
  override val transformation: String => String = mapping.transformation

  def asHeader: CARMLChannelMetadataHeader = CARMLChannelMetadataHeader(
    name=name,
    author=author,
    tags=tags,
    mapping=mapping.asHeader
  )
}

case class CARMLChannelMetadataHeader(
                                 name: Option[String] = None,
                                 author: Option[String] = None,
                                 tags: Set[String] = Set(),
                                 mapping: RMLMappingHeader
                               )

//object CARMLChannelMetadata {
//  def apply(name: Option[String],
//            author: Option[String],
//            inputFormat: InputFormat,
//            outputFormat: OutputFormat,
//            rmlMapping: String) =
//    new CARMLChannelMetadata(
//      name = name,
//      author = author,
//      inputFormat = inputFormat,
//      outputFormat = outputFormat,
//      rmlMapping = rmlMapping
//    )
//}