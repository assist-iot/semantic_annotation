package org.sripas.seaman
package streaming.core.channels

import streaming.core.channels.streams.StreamControl
import streaming.core.settings.MaterializedChannelSettings

case class MaterializedChannel[M](
                                   settings: MaterializedChannelSettings,
                                   streamControl: StreamControl,
                                   transformation: String => String,
                                   metadata: M
                                 )
