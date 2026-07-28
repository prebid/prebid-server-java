package org.prebid.server.functional.model.request

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.ChannelType

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Channel {

    ChannelType name
    String version
}
