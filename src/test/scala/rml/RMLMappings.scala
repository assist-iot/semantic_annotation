package org.sripas.seaman
package rml

import rml.utils.RMLProcessorResources

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.Success

class RMLMappings extends AnyFlatSpec with should.Matchers with RMLProcessorResources {

  // TODO: Cover much more RML mappers with tests

  "SemAnnDemo RML Mapper from JSON" should "accept \"try\" input" in {
    // TODO: Add other mappers to this test
    rmlMappings.semAnnDemo.tryTransformation() shouldBe a[Success[String]]
  }

}
