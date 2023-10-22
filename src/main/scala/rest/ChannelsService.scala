package org.sripas.seaman
package rest

import control.CARMLChannelController
import rml.CARMLChannelMetadata
import rml.exceptions.RMLMappingException
import rml.json.RMLJsonSupport
import streaming.core.channels._
import streaming.core.exceptions.{NoSuchChannelException, NonUniqueChannelIdException, WrongSettingsException}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import org.slf4j.LoggerFactory

class ChannelsService[A](
                          channelController: CARMLChannelController
                        ) extends RestService with RMLJsonSupport {

  private val logger = LoggerFactory.getLogger(this.getClass)

  // TODO: Use ChannelController instead of "inline" operations with ChannelManager

  override val routes: Route =
    handleExceptions(channelsExceptionHandler) {
      concat(
        getAllChannels,
        addChannel,
        channelByIdRoutes
      )
    }

  def channelsExceptionHandler: ExceptionHandler = ExceptionHandler { exception =>
    //    logger.error(exception.getMessage, exception)
    logger.error(exception.getMessage)
    exception match {
      case e: RMLMappingException => complete(StatusCodes.BadRequest, s"RML mapping exception: ${e.getMessage}")
      case e: NonUniqueChannelIdException => complete(StatusCodes.BadRequest, s"Channel ID exception: ${e.getMessage}")
      case e: NoSuchChannelException => complete(StatusCodes.NotFound, s"Channel ID not found: ${e.getMessage}")
      case e: WrongSettingsException => complete(StatusCodes.UnprocessableEntity, s"Wrong settings: ${e.getMessage}")
      case e: Exception => complete(StatusCodes.InternalServerError, s"Channel management error: ${e.getMessage}")
    }
  }

  private case class FilterParams(
                                   includeAll: Boolean,
                                   includeStatus: Boolean,
                                   includeMetadata: Boolean,
                                   includeSettings: Boolean,
                                   includeRml: Boolean
                                 )

  def filterParams(paramRoute: FilterParams => Route): Route = parameters(
    "all".optional,
    "status".optional,
    "metadata".optional,
    "settings".optional,
    "rml".optional) { (all, status, metadata, settings, rml) =>
    paramRoute(
      FilterParams(
        includeAll = booleanParamTrue(all),
        includeStatus = booleanParamTrue(status),
        includeMetadata = booleanParamTrue(metadata),
        includeSettings = booleanParamTrue(settings),
        includeRml = booleanParamTrue(rml)
      )
    )
  }

  def getAllChannels: Route =
    pathEndOrSingleSlash {
      get {
        filterParams(filterParams =>
          onSuccess(
            channelController.getAllChannelsAsJson(
              filterParams.includeAll,
              filterParams.includeStatus,
              filterParams.includeMetadata,
              filterParams.includeSettings,
              filterParams.includeRml
            )) {
            complete(_)
          }
        )
      }
    }

  def addChannel: Route =
    pathEndOrSingleSlash {
      post {
        entity(as[ChannelInfo[CARMLChannelMetadata]]) { chInfo =>
          onSuccess(channelController.addChannel(chInfo)) { materializedChannel =>
            complete(materializedChannel.settings.channelId)
          }
        }
      }
    }

  def channelByIdRoutes: Route =
    pathPrefix(Segment)(segment => concat(
      getChannel(segment),
      removeChannel(segment),
      updateChannelSettings(segment),
      restartChannel(segment)
    ))


  def getChannel(channelId: String): Route =
    pathEndOrSingleSlash {
      get {
        filterParams(filterParams =>
          onSuccess(channelController.getChannelAsJson(channelId)(
            filterParams.includeAll,
            filterParams.includeStatus,
            filterParams.includeMetadata,
            filterParams.includeSettings,
            filterParams.includeRml
          )) {
            complete(_)
          }
        )
      }
    }

  def removeChannel(channelId: String): Route =
    pathEndOrSingleSlash {
      delete {
        onSuccess(channelController.stopAndRemoveChannel(channelId)) { done =>
          complete(s"Stopped and removed channel with ID: $channelId")
        }
      }
    }

  def restartChannel(channelId: String): Route =
    pathPrefix("restart") {
      pathEndOrSingleSlash {
        patch {
          onSuccess(channelController.restartChannel(channelId)) { newMaterializedChannel =>
            complete(s"Restarted channel with ID: $channelId")
          }
        }
      }
    }


  def updateChannelSettings(channelId: String): Route =
    pathEndOrSingleSlash {
      patch {
        entity(as[ChannelStatusInfoOptions]) { channelStatusInfoOptions =>

          onSuccess(channelController.updateChannelStatus(channelId)(channelStatusInfoOptions)) { changesMade =>
            if (!changesMade.updatesMade) {
              complete(s"No updates were made to channel status for channel with ID: $channelId")
            } else if (!changesMade.channelRunningUpdated) {
              complete(s"Updated settings for channel with ID: $channelId")
            } else if (changesMade.channelStopped) {
              complete(s"Updated settings and stopped channel with ID: $channelId")
            } else {
              complete(s"Updated settings and started channel with ID: $channelId")
            }
          }
        }
      }
    }
}
