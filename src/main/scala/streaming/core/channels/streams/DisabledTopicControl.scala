package org.sripas.seaman
package streaming.core.channels.streams

class DisabledTopicControl(topicPermanentlyEnabled: Boolean) extends TopicControl {

  override def canBeControlled(): Boolean = false

  override def isEnabled(): Boolean = topicPermanentlyEnabled

  override def enable(): Boolean = isEnabled()

  override def disable(): Boolean = isEnabled()
}

object DisabledTopicControl {
  def apply(topicPermanentlyEnabled: Boolean = false) = new DisabledTopicControl(topicPermanentlyEnabled)
}