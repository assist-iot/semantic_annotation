package org.sripas.seaman
package rest

import akka.http.scaladsl.server.Route

object SwaggerService extends RestService {

  override val routes: Route = swaggerFromFileRoute

  def swaggerFromFileRoute: Route = concat(
    pathSingleSlash {
      getFromResource("swagger/index.html")
    },
    getFromResourceDirectory("swagger")
  )

}
