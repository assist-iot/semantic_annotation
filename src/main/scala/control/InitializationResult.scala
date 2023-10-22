package org.sripas.seaman
package control

import control.InitializationStatus.InitializationStatus

case class InitializationResult(
                                 status: InitializationStatus,
                                 error: Option[String] = None
                               )

object InitializationResults {
  def initializing: InitializationResult = InitializationResult(
    status = InitializationStatus.INITIALIZING
  )

  def initialized: InitializationResult = InitializationResult(
    status = InitializationStatus.INITIALIZED)

  def error(exception: Throwable): InitializationResult = InitializationResult(
    status = InitializationStatus.ERROR,
    error = Some(s"$exception")
  )

  def criticalError(exception: Throwable): InitializationResult = InitializationResult(
    status = InitializationStatus.CRITICAL_ERROR,
    error = Some(s"$exception")
  )
}
