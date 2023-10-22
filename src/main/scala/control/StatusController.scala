package org.sripas.seaman
package control

import akka.Done

import scala.concurrent.Future

trait StatusController {

  def initialize: Future[Done]

  def shutdown: Future[Done]

  def initializationResult: Future[InitializationResult]

}
