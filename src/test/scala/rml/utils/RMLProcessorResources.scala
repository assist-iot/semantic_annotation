package org.sripas.seaman
package rml.utils

import rml.{InputFormat, OutputFormat, RMLMapping, RMLProcessor}
import utils.TestUtils

trait RMLProcessorResources extends TestUtils {

  object inputs {
    object json {
      val validSensor: String = "{ \"id\": \"sensors/sensor1\",  \"type\": \"Sensor\",  \"location\": {    \"type\": \"Point\",    \"coordinates\": {      \"latitude\": \"51.0500000\",      \"longitude\": \"3.7166700\"    }  }}"
      val validSimpleObject: String = "{ \"key\": \"value\" }"
      val invalidSimpleObject: String = "{ \"key\": \"value\""

      object fromFiles {
        val input: String = resourceAsString("data/sources/input.json")
        val people: String = resourceAsString("data/sources/People.json")
        val semAnnDemoInput: String = resourceAsString("data/sources/SemAnnDemoInput.json")
        val pracownicy: String = resourceAsString("data/sources/pracownicy.json")
        val lokalizacja: String = resourceAsString("data/sources/lokalizacja.json")
        val kamera: String = resourceAsString("data/sources/kamera.json")
      }
    }

    object xml {
      val validSensor: String = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?> <sensor>      <id>sensors/sensor1</id>      <type>Sensor</type>      <location>          <type>Point</type>          <coordinates>              <latitude>51.0500000</latitude>              <longitude>3.7166700</longitude>          </coordinates>      </location>  </sensor>"
      val validSimpleObject: String = "<key>value</key>"
      val invalidSimpleObject: String = "<key>value</key"

      object fromFiles {
        val input: String = resourceAsString("data/sources/input.xml")
        val people: String = resourceAsString("data/sources/People.xml")
        val semAnnDemo: String = resourceAsString("data/sources/SemAnnDemoInput.xml")
      }
    }

    object csv {
      val nonemptyCsv: String = "1,2,3"
    }
  }

  object mappings {
    object fromFiles {
      val amount: String = resourceAsString("data/mappings/amountMapping.ttl")
      val amountStreamSource: String = resourceAsString("data/mappings/amountMappingStreamSource.ttl")
      val peopleDemo: String = resourceAsString("data/mappings/PeopleDemo.ttl")
      val semAnnDemo: String = resourceAsString("data/mappings/SemAnnDemoSource.ttl")
      val semAnnDemoWithIRI: String = resourceAsString("data/mappings/SemAnnDemoSourceWithIRI.ttl")
      val pracownicy: String = resourceAsString("data/mappings/pracownicy.ttl")
      val kamera: String = resourceAsString("data/mappings/kamera_v2.ttl")
      val lokalizacja: String = resourceAsString("data/mappings/lokalizacja_v2.ttl")
    }
  }

  object validators {
    val json: String => Unit = RMLProcessor.getJSONFormatValidator
    val xml: String => Unit = RMLProcessor.getXMLFormatValidator
    val csv: String => Unit = RMLProcessor.getCSVFormatValidator
    val mock: String => Unit = RMLProcessor.getMockFormatValidator
    val nonEmptyInput: String => Unit = RMLProcessor.getMockFormatValidator
  }

  object mappers {
    val vanillaRMLAmount: String => String = RMLProcessor.getMapper(
      mappings.fromFiles.amount,
      InputFormat.JSON,
      OutputFormat.TTL
    )
  }

  object rmlMappings {
    val semAnnDemo: RMLMapping = RMLMapping(
      name = Some("SemAnn demo mapping"),
      author = Some("szmejap"),
      version = Some("1.0"),
      description = Some("demo mapping"),
      tags = Set("demo", "geolocation"),
      inputFormat = InputFormat.JSON,
      outputFormat = OutputFormat.TTL,
      rml = mappings.fromFiles.semAnnDemo
    )

    val semAnnDemoWithIRI: RMLMapping = RMLMapping(
      name = Some("SemAnn demo mapping"),
      author = Some("szmejap"),
      version = Some("1.0"),
      description = Some("demo mapping with IRI"),
      tags = Set("demo", "geolocation"),
      inputFormat = InputFormat.JSON,
      outputFormat = OutputFormat.TTL,
      rml = mappings.fromFiles.semAnnDemoWithIRI
    )
  }
}
