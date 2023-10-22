package org.sripas.seaman
package rest

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Directives, Route}

trait RestService extends Directives with SprayJsonSupport {

  val routes: Route

  def booleanParamTrue(param: Option[String]) =
    param.isDefined && param.get != "false" && param.get != "0"

}
