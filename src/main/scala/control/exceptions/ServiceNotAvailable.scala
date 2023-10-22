package org.sripas.seaman
package control.exceptions

class ServiceNotAvailable(message: String, cause: Throwable = null) extends UnsupportedOperationException(message, cause) {
    def this(cause: Throwable) = this(if (cause != null) cause.toString else null, cause)
    def this() = this(null)
}
