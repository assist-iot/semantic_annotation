package org.sripas.seaman
package streaming.core.channels.streams

class BasicTopicControl(initiallyEnabled: Boolean) extends TopicControl {

  @volatile private var enabled = initiallyEnabled

  override def canBeControlled(): Boolean = true

  override def isEnabled(): Boolean = enabled

  override def enable(): Boolean = {
    enabled = true
    enabled
  }

  override def disable(): Boolean = {
    enabled = false
    enabled
  }
}

object BasicTopicControl {
  def apply(initiallyEnabled: Boolean = true) = new BasicTopicControl(initiallyEnabled)
}