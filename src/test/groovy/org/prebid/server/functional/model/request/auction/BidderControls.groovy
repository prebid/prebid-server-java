package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class BidderControls {

    GenericPreferredBidder generic
    @JsonProperty("GeNeRiC")
    GenericPreferredBidder genericAnyCase
}
