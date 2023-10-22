package org.sripas.seaman
package rest

import control.RMLAnnotationsController
import control.exceptions.ServiceNotAvailable
import rml.RMLMapping
import rml.exceptions.NoSuchAnnotationException
import rml.json.RMLJsonSupport

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import org.slf4j.LoggerFactory

class AnnotationsService(
                          annotationsController: RMLAnnotationsController
                        ) extends RestService with RMLJsonSupport {

  private val logger = LoggerFactory.getLogger(this.getClass)

  override val routes: Route =
    handleExceptions(annotationsExceptionHandler) {
      concat(
        getAllAnnotations,
        addAnnotation,
        annotationByIdRoutes
      )
    }

  def annotationsExceptionHandler: ExceptionHandler = ExceptionHandler { exception =>
    //    logger.error(exception.getMessage, exception)
    logger.error(exception.getMessage)
    // TODO: Handle more exceptions
    exception match {
      case e: NoSuchAnnotationException => complete(StatusCodes.NotFound, s"Annotation ID not found: ${e.getMessage}")
      case e: ServiceNotAvailable => complete(StatusCodes.NotFound, s"Service not available: ${e.getMessage}")
      case e: Exception => complete(StatusCodes.InternalServerError, s"Annotations management error: ${e.getMessage}")
    }
  }

  private case class FilterParams(
                                   includeAll: Boolean,
                                   includeHeader: Boolean,
                                   includeRml: Boolean
                                 )

  def filterParams(paramRoute: FilterParams => Route): Route = parameters(
    "all".optional,
    "header".optional,
    "rml".optional) { (all, header, rml) =>
    paramRoute(
      FilterParams(
        includeAll = booleanParamTrue(all),
        includeHeader = booleanParamTrue(header),
        includeRml = booleanParamTrue(rml)
      )
    )
  }

  def getAllAnnotations: Route = {
    pathEndOrSingleSlash {
      get {
        filterParams( filterParams =>
          onSuccess(annotationsController.getAllAnnotationsAsJson(
            includeAll = filterParams.includeAll,
            includeHeader = filterParams.includeHeader,
            includeRml = filterParams.includeRml
          )){
            complete(_)
          }
        )
      }
    }
  }

  def addAnnotation: Route =
    pathEndOrSingleSlash {
      post {
        entity(as[RMLMapping]) { rmlMapping =>
          onSuccess(annotationsController.addAnnotation(rmlMapping)) { mappingId =>
            complete(mappingId)
          }
        }
      }
    }

  def annotationByIdRoutes: Route =
    pathPrefix(Segment)(segment =>
      concat(
        getAnnotation(segment),
        removeAnnotation(segment)
      )
    )

  def getAnnotation(annotationId: String): Route =
    pathEndOrSingleSlash {
      get {
        filterParams(filterParams =>
          onSuccess(annotationsController.getAnnotationAsJson(annotationId)(
            filterParams.includeAll,
            filterParams.includeHeader,
            filterParams.includeRml
          )) {
            complete(_)
          }
        )
      }
    }

  def removeAnnotation(annotationId: String): Route =
    pathEndOrSingleSlash {
      delete {
        onSuccess(annotationsController.removeAnnotation(annotationId)) { done =>
          complete(s"Removed annotation with ID: $annotationId")
        }
      }
    }
}
