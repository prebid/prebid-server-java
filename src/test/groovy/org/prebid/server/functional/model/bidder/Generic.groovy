package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonProperty
import org.prebid.server.functional.model.response.auction.MediaType

class Generic implements BidderAdapter {

    Object exampleProperty
    Integer firstParam
    Integer secondParam
    @JsonProperty("dealsonly")
    Boolean dealsOnly
    @JsonProperty("pgdealsonly")
    Boolean pgDealsOnly
    @JsonProperty("prefmtype")
    MediaType preferredMediaType
}
