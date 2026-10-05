package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Interstitial {

    @JsonProperty("minwidthperc")
    Integer minWidthPercentage
    @JsonProperty("minheightperc")
    Integer minHeightPercentage
}
