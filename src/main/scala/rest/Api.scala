package org.sripas.seaman
package rest

import control.{CARMLChannelController, RMLAnnotationsController, StatusController}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}

class Api[A](
              channelController: CARMLChannelController,
              annotationsController: RMLAnnotationsController,
              statusController: StatusController
            ) extends Directives {

  val channelsService = new ChannelsService[A](channelController)

  val annotationsService = new AnnotationsService(annotationsController)

  val statusService = new StatusService(statusController)

  val routes: Route = concat(
    pathPrefix("channels") {
      channelsService.routes
    },
    pathPrefix("annotations") {
      annotationsService.routes
    },
    pathPrefix("swagger") {
      pathEnd {
        redirect("/swagger/", StatusCodes.TemporaryRedirect)
      } ~ SwaggerService.routes
    },
    pathPrefix("version") {
      VersionService.routes
    },
    pathPrefix("settings") {
      SettingsService.routes
    },
    pathPrefix("status") {
      statusService.routes
    }
  )
}
