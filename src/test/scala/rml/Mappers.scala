package org.sripas.seaman
package rml

import rml.utils.RMLProcessorResources

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class Mappers extends AnyFlatSpec with should.Matchers with RMLProcessorResources {

  // TODO: Cover much more mappers with tests

  "Vanilla Mapper from JSON" should "not be accepted by CARML" in {
    a[Exception] should be thrownBy {
      mappers.vanillaRMLAmount(inputs.json.validSensor)
    }
  }
  it should "not accept empty input" in {
    a[spray.json.JsonParser.ParsingException] should be thrownBy {
      mappers.vanillaRMLAmount("")
    }
  }
  it should "not accept invalid input" in {
    a[spray.json.JsonParser.ParsingException] should be thrownBy {
      mappers.vanillaRMLAmount(inputs.json.invalidSimpleObject)
    }
  }
  it should "not accept input in invalid format" in {
    a[spray.json.JsonParser.ParsingException] should be thrownBy {
      mappers.vanillaRMLAmount(inputs.xml.validSensor)
    }
  }
}
