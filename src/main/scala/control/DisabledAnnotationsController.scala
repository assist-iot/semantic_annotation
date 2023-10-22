package org.sripas.seaman
package control
import control.exceptions.ServiceNotAvailable
import rml.RMLMapping

import akka.Done
import spray.json.JsObject

import scala.concurrent.Future

class DisabledAnnotationsController(val message: String) extends RMLAnnotationsController {

  private def throwException = throw new ServiceNotAvailable(message)

  override def getAllAnnotations: Future[Map[String, RMLMapping]] = throwException

  override def getAllAnnotationsAsJson(includeAll: Boolean, includeHeader: Boolean, includeRml: Boolean): Future[Map[String, JsObject]] = throwException

  override def addAnnotation(rmlMapping: RMLMapping): Future[String] = throwException

  override def getAnnotation(annotationId: String): Future[RMLMapping] = throwException

  override def getAnnotationAsJson(annotationId: String)(includeAll: Boolean, includeHeader: Boolean, includeRml: Boolean): Future[JsObject] = throwException

  override def removeAnnotation(annotationId: String): Future[Done] = throwException
}
