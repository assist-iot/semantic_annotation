package org.sripas.seaman
package rml.exceptions

class RMLMappingException(message: String, cause: Throwable = null) extends RuntimeException(message, cause) {
  def this(cause: Throwable) = this(if (cause != null) cause.toString else null, cause)
  def this() = this(null)
}
