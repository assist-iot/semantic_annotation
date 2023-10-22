package org.sripas.seaman
package streaming.json

import streaming.core.utils.BrokerType
import streaming.core.utils.BrokerType.BrokerType

import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

trait UtilsJsonSupport extends DefaultJsonProtocol {

  implicit val brokerTypeFormat: JsonFormat[BrokerType] = BrokerTypeFormat

  private object BrokerTypeFormat extends JsonFormat[BrokerType] {
    override def write(obj: BrokerType): JsValue = JsString(obj.toString())

    override def read(json: JsValue): BrokerType = BrokerType.withName(json.convertTo[String])
  }

}
