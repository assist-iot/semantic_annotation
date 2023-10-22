package org.sripas.seaman
package rml.exceptions

class NoSuchAnnotationException(message: String, cause: Throwable = null) extends IllegalArgumentException(message, cause) {
  def this(cause: Throwable) = this(if (cause != null) cause.toString else null, cause)
  def this() = this(null)
}
