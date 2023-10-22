package org.sripas.seaman
package rest

import control.StatusController
import control.json.StatusJsonSupport

import akka.http.scaladsl.server.Route

class StatusService(statusController: StatusController) extends RestService with StatusJsonSupport {

  override val routes: Route = getStatus

  def getStatus: Route = {
    pathEndOrSingleSlash {
      get {
        onSuccess(statusController.initializationResult){
          complete(_)
        }
      }
    }
  }

}
