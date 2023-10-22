package org.sripas.seaman
package utils

import scala.io.Source

trait TestUtils {

  def resourceAsString(resourcePath: String): String = {
    val source = Source.fromURL(getClass.getClassLoader.getResource(resourcePath))
    val sourceAsStr = source.mkString
    source.close()
    sourceAsStr
  }

}
