package org.sripas.seaman
package streaming.core.channels

case class ChannelStatusInfo(
                              isStopped: Boolean = false,
                              inputMonitorTopicEnabled: Boolean = true,
                              outputMonitorTopicEnabled: Boolean = true,
                              errorTopicEnabled: Boolean = true,
                              error: Option[String] = None
                            ) {
  override def toString: String =
    s"ChannelStatusInfo(isStopped: $isStopped, inputMonitoringTopicEnabled: $inputMonitorTopicEnabled, outputMonitoringTopicEnabled: $outputMonitorTopicEnabled, errorTopicEnabled: $errorTopicEnabled, error: $error)"
}

case class ChannelStatusInfoOptions(
                                     isStopped: Option[Boolean] = None,
                                     inputMonitorTopicEnabled: Option[Boolean] = None,
                                     outputMonitorTopicEnabled: Option[Boolean] = None,
                                     errorTopicEnabled: Option[Boolean] = None,
                                     error: Option[String] = None
                                   ) {
  def toChannelStatusInfoWithDefaults: ChannelStatusInfo = {
    val defaults = ChannelStatusInfo()
    ChannelStatusInfo(
      isStopped = isStopped.getOrElse(defaults.isStopped),
      inputMonitorTopicEnabled = inputMonitorTopicEnabled.getOrElse(defaults.inputMonitorTopicEnabled),
      outputMonitorTopicEnabled = outputMonitorTopicEnabled.getOrElse(defaults.outputMonitorTopicEnabled),
      errorTopicEnabled = errorTopicEnabled.getOrElse(defaults.errorTopicEnabled)
    )
  }

  override def toString: String =
    s"ChannelStatusInfo(isStopped: $isStopped, inputMonitoringTopicEnabled: $inputMonitorTopicEnabled, outputMonitoringTopicEnabled: $outputMonitorTopicEnabled, errorTopicEnabled: $errorTopicEnabled, error: $error)"
}
