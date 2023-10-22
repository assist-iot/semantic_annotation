package org.sripas.seaman
package control.json

import control.{InitializationResult, InitializationStatus}
import control.InitializationStatus.InitializationStatus

import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

trait StatusJsonSupport extends DefaultJsonProtocol {

  implicit val initializationStatusFormat: JsonFormat[InitializationStatus] = InitializationStatusFormat
  implicit val initializationResultFormat: RootJsonFormat[InitializationResult] = jsonFormat2(InitializationResult)

  private object InitializationStatusFormat extends JsonFormat[InitializationStatus] {
    override def write(obj: InitializationStatus): JsValue = JsString(obj.toString)

    override def read(json: JsValue): InitializationStatus = InitializationStatus.withName(json.convertTo[String])
  }

}
