package org.sripas.seaman
package streaming.core.channels

import streaming.core.settings.ChannelSettings

case class ChannelInfo[M](
                           settings: ChannelSettings,
                           metadata: M,
                           status: ChannelStatusInfo = ChannelStatusInfo()
                         )
