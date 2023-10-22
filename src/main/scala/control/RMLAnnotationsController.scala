package org.sripas.seaman
package control

import rml.RMLMapping

import akka.Done
import spray.json.JsObject

import scala.concurrent.Future

trait RMLAnnotationsController {

  def getAllAnnotations: Future[Map[String, RMLMapping]]

  def getAllAnnotationsAsJson(
                               includeAll: Boolean,
                               includeHeader: Boolean,
                               includeRml: Boolean
                             ): Future[Map[String, JsObject]]

  def addAnnotation(rmlMapping: RMLMapping): Future[String]

  def getAnnotation(annotationId: String): Future[RMLMapping]

  def getAnnotationAsJson(
                           annotationId: String
                         )(
                           includeAll: Boolean,
                           includeHeader: Boolean,
                           includeRml: Boolean
                         ): Future[JsObject]

  def removeAnnotation(annotationId: String): Future[Done]

}
