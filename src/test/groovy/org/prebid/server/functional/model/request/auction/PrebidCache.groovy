package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class PrebidCache {

    Boolean winningOnly
    PrebidCacheSettings bids
    @JsonProperty("vastxml")
    PrebidCacheSettings vastXml
}
