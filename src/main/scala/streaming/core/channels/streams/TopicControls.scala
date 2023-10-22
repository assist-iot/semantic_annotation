package org.sripas.seaman
package streaming.core.channels.streams

case class TopicControls(
                          inputMonitor: TopicControl,
                          outputMonitor: TopicControl,
                          error: TopicControl
                        )
