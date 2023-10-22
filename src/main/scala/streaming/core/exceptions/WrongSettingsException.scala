package org.sripas.seaman
package streaming.core.exceptions

class WrongSettingsException(message: String, cause: Throwable = null) extends IllegalArgumentException(message, cause) {
  def this(cause: Throwable) = this(if (cause != null) cause.toString else null, cause)

  def this() = this(null)
}
