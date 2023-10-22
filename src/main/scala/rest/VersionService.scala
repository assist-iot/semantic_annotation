package org.sripas.seaman
package rest

import akka.http.scaladsl.server.Route
import buildinfo.BuildInfo
import spray.json.DefaultJsonProtocol

case class VersionInfo(
                      name: String,
                      version: String
                      )

trait VersionInfoJsonSupport extends DefaultJsonProtocol {
  implicit val versionInfoFormat = jsonFormat2(VersionInfo)
}

object VersionService extends RestService with VersionInfoJsonSupport {

  override val routes: Route = versionRoute

  val versionInfo = VersionInfo(BuildInfo.name, BuildInfo.version)

  def versionRoute: Route =
      get {
        complete(versionInfo)
      }
}
