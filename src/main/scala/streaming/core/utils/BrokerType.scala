package org.sripas.seaman
package streaming.core.utils

object BrokerType extends Enumeration {
  type BrokerType = Value
  val MQTT, KAFKA = Value
}
