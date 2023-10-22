package org.sripas.seaman
package rml

import rml.utils.RMLProcessorResources

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class InputValidators extends AnyFlatSpec with should.Matchers with RMLProcessorResources {

  "JSON input validator" should "Accept valid JSON input" in {
    noException should be thrownBy validators.json(inputs.json.validSensor)
    noException should be thrownBy validators.json(inputs.json.validSimpleObject)

    noException should be thrownBy validators.json(inputs.json.fromFiles.input)
    noException should be thrownBy validators.json(inputs.json.fromFiles.people)
    noException should be thrownBy validators.json(inputs.json.fromFiles.semAnnDemoInput)
  }
  it should "reject invalid JSON input" in {
    a [spray.json.JsonParser.ParsingException] should be thrownBy {
      validators.json(inputs.json.invalidSimpleObject)
    }
  }
  it should "reject empty input" in {
    a [spray.json.JsonParser.ParsingException] should be thrownBy {
      validators.json("")
    }
  }

  "XML input validator" should "Accept valid XML input" in {
    noException should be thrownBy validators.xml(inputs.xml.validSensor)
    noException should be thrownBy validators.xml(inputs.xml.validSimpleObject)

    noException should be thrownBy validators.xml(inputs.xml.fromFiles.input)
    noException should be thrownBy validators.xml(inputs.xml.fromFiles.people)
    noException should be thrownBy validators.xml(inputs.xml.fromFiles.semAnnDemo)
  }
  it should "reject invalid XML input" in {
    a [org.xml.sax.SAXParseException] should be thrownBy {
      validators.xml(inputs.xml.invalidSimpleObject)
    }
  }
  it should "reject empty input" in {
    a [org.xml.sax.SAXParseException] should be thrownBy {
      validators.xml("")
    }
  }

  "CSV input validator" should "Accept non-empty input" in {
    noException should be thrownBy validators.csv(inputs.csv.nonemptyCsv)
  }
  it should "reject empty input" in {
    a [Exception] should be thrownBy {
      validators.csv("")
    }
  }
  // TODO: More CSV validation?

}
