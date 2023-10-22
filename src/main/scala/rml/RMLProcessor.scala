package org.sripas.seaman
package rml

import rml.InputFormat._
import rml.OutputFormat._

import com.taxonic.carml.engine.RmlMapper
import com.taxonic.carml.logical_source_resolver.{CsvResolver, JsonPathResolver, XPathResolver}
import com.taxonic.carml.util.RmlMappingLoader
import com.taxonic.carml.vocab.Rdf
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import java.io.{ByteArrayInputStream, StringWriter}
import java.text.Normalizer.Form

import spray.json._

object RMLProcessor {

  def getMapper(
                 rmlMapping: String,
                 inputFormat: InputFormat,
                 outputFormat: OutputFormat
               ): String => String = {

    val mapping = RmlMappingLoader.build().load(
      RDFFormat.TURTLE,
      new ByteArrayInputStream(rmlMapping.getBytes())
    )

    val builderStub = RmlMapper.newBuilder()

    val (builderWithFormat, inputValidator) = inputFormat match {
      case JSON => (builderStub.setLogicalSourceResolver(Rdf.Ql.JsonPath, new JsonPathResolver()), getJSONFormatValidator)
      case XML => (builderStub.setLogicalSourceResolver(Rdf.Ql.XPath, new XPathResolver()), getXMLFormatValidator)
      case CSV => (builderStub.setLogicalSourceResolver(Rdf.Ql.Csv, new CsvResolver()), getCSVFormatValidator)
    }

    val mapper = builderWithFormat
      .iriUnicodeNormalization(Form.NFKC)
      .iriUpperCasePercentEncoding(false)
      .build()

    val outputRDFFormat = outputFormat match {
      case TTL => RDFFormat.TURTLE
      case JSONLD => RDFFormat.JSONLD
    }

    val mappingFunction = (input: String) => {
      inputValidator(input) // Throws exception, if the input format is invalid
      val inputStream = new ByteArrayInputStream(input.getBytes())
      mapper.bindInputStream(inputStream)
      val resultModel = mapper.map(mapping)
      val stringWriter = new StringWriter()

      Rio.write(resultModel, stringWriter, outputRDFFormat)

      stringWriter.toString
    }

    mappingFunction
  }

  def getJSONFormatValidator: String => Unit = (input: String) => input.parseJson

  def getXMLFormatValidator: String => Unit = (input: String) => scala.xml.XML.loadString(input)

  // TODO: How to validate CSV?
  def getCSVFormatValidator: String => Unit = getNonEmptyInputValidator

  def getNonEmptyInputValidator: String => Unit = (input: String) => if (input.isEmpty) throw new IllegalArgumentException("Empty input is not allowed!")

  def getMockFormatValidator: String => Unit = (input: String) => ()

}
