package org.sripas.seaman
package rml

import rml.InputFormat._
import rml.OutputFormat.OutputFormat
import rml.exceptions.RMLMappingException
import streaming.core.utils.Transformer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter
import scala.util.Try

case class RMLMapping(
                       name: Option[String] = None,
                       author: Option[String] = None,
                       version: Option[String] = None,
                       description: Option[String] = None,
                       tags: Set[String] = Set(),
                       inputFormat: InputFormat,
                       outputFormat: OutputFormat,
                       rml: String
                     ) extends Transformer {

  def asHeader: RMLMappingHeader = RMLMappingHeader(
    name = name,
    author = author,
    version = version,
    description = description,
    tags = tags,
    inputFormat = inputFormat,
    outputFormat = outputFormat,
    rmlHash = rmlHash
  )

  val rmlHash: String = DatatypeConverter.printHexBinary(
    MessageDigest
      .getInstance("SHA-256")
      .digest(rml.getBytes(StandardCharsets.UTF_8))
  )

  override val transformation: String => String = RMLProcessor.getMapper(rml, inputFormat, outputFormat)

  /**
   * Sends test data through the transformation using this RMLMapping and returns result, or errors (if any)
   *
   * @param input Input with which the transformation will be tested. If missing, example data with format decided by
   *              {@link #inputFormat} will be used.
   *
   * @return Result of applying the transformation to input data
   */
  def tryTransformation(input: Option[String] = None): Try[String] =
    Try {
      tryTransformationWithException(input)
    }

  /**
   * Sends test data through the transformation using this RMLMapping and returns result, or errors (if any).
   *
   * throws exceptions, if the transformation would throw exceptions with given input data.
   *
   * @param input Input with which the transformation will be tested. If missing, example data with format decided by
   *              {@link #inputFormat} will be used.
   * @return Result of applying the transformation to input data
   */
  def tryTransformationWithException(input: Option[String] = None): String = {
    val testInput = input match {
      case Some(value) => value
      case None => inputFormat match {
        case JSON => tryInputData.json
        case CSV => tryInputData.csv
        case XML => tryInputData.xml
      }
    }
    try {
        transformation(testInput)
    } catch {
      case exception: Throwable => throw new RMLMappingException(s"Error when trying out RML mapping: ${exception.getMessage} cause: ${exception.getCause.getMessage}", exception)
    }
  }

  /**
   * Example input data used in trying out mappings, to see if they are syntactically valid
   */
  object tryInputData {
    val json = "{\"key\": \"value\"}"
    val csv = "1, 2, 3"
    val xml = "<tag>text</tag>"
  }
}

case class RMLMappingHeader(
                             name: Option[String] = None,
                             author: Option[String] = None,
                             version: Option[String] = None,
                             description: Option[String] = None,
                             tags: Set[String] = Set(),
                             inputFormat: InputFormat,
                             outputFormat: OutputFormat,
                             rmlHash: String
                           )
