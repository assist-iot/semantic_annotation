package org.sripas.seaman
package streaming.core.settings

protected class MQTTBroker(protocol: String,
                           host: String,
                           port: Int) {
  val broker: String = s"$protocol://$host:$port"
}

case class MQTTSettings(
                         protocol: String,
                         host: String,
                         port: Int,
                         clientId: Option[String],
                         user: Option[String],
                         password: Option[String]
                       ) extends MQTTBroker(protocol, host, port) {
  def materialize(defaultMQTTClientId: String): MaterializedMQTTSettings = {
    MaterializedMQTTSettings(
      protocol,
      host,
      port,
      clientId.getOrElse(defaultMQTTClientId),
      user,
      password
    )
  }

  def withClientId(newClientId: String): MQTTSettings = MQTTSettings(protocol, host, port, Some(newClientId), user, password)
}

case class MaterializedMQTTSettings(
                                     protocol: String,
                                     host: String,
                                     port: Int,
                                     clientId: String,
                                     user: Option[String],
                                     password: Option[String]
                                   ) extends MQTTBroker(protocol, host, port) {

  def withClientId(newClientId: String): MaterializedMQTTSettings = MaterializedMQTTSettings(protocol, host, port, newClientId, user, password)

}
