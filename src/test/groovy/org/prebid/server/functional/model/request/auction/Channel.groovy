package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.ChannelType

@ToString(includeNames = true, ignoreNulls = true)
class Channel {

    ChannelType name
    String version
}
