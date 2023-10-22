package org.sripas.seaman
package streaming.core.channels.streams

trait TopicControl {

  /**
   * Informs, whether this topic can be enabled or disabled.
   * If false, calling {@link # enable ( )} or {@link # disable ( )} should not have any effect on the topic
   *
   * @return
   */
  def canBeControlled(): Boolean

  def isEnabled(): Boolean

  def isDisabled(): Boolean = !isEnabled()

  /**
   * Attempts to enable the topic.
   *
   * If the topic was previously enabled, nothing will be done.
   *
   * @return New state of the topic true - enabled, false - disabled
   */
  def enable(): Boolean

  /**
   * Attempts to disable the topic.
   *
   * If the topic was previously disabled, nothing will be done.
   *
   * @return New state of the topic true - enabled, false - disabled
   */
  def disable(): Boolean

}
