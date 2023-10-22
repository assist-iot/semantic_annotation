package org.sripas.seaman
package streaming.core.settings

case class BrokerSettingsGroup[B](
                                   inputTopicBrokerSettings: B,
                                   outputTopicBrokerSettings: B,
                                   monitorInputTopicBrokerSettings: B,
                                   monitorOutputTopicBrokerSettings: B,
                                   errorTopicBrokerSettings: B
                                 )

