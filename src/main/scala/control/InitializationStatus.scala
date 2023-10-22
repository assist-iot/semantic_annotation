package org.sripas.seaman
package control

object InitializationStatus extends Enumeration {
  type InitializationStatus = Value
  val INITIALIZED, INITIALIZING, ERROR, CRITICAL_ERROR = Value
}
