package org.sripas.seaman
package streaming.core.settings

protected class KafkaBootstrapServers(host: String, port: Int){
  val bootstrapServers: String = s"$host:$port"
}

case class KafkaSettings(
                          host: String,
                          port: Int,
                          groupId: Option[String] = None,
                        ) extends KafkaBootstrapServers(host, port) {
  def materialize(defaultGroupId: String): MaterializedKafkaSettings =
    MaterializedKafkaSettings(
      host,
      port,
      groupId.getOrElse(defaultGroupId)
    )
}

case class MaterializedKafkaSettings(
                                      host: String,
                                      port: Int,
                                      groupId: String
                                    ) extends KafkaBootstrapServers(host,port)
