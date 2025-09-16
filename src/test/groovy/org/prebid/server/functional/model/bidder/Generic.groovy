package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class Generic implements BidderAdapter {

    Object exampleProperty
    Integer firstParam
    Integer secondParam
    @JsonProperty("dealsonly")
    Boolean dealsOnly
    @JsonProperty("pgdealsonly")
    Boolean pgDealsOnly
}
