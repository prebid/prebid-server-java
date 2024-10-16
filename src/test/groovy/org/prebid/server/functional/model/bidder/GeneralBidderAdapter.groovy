package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonProperty

class GeneralBidderAdapter implements BidderAdapter {

    Object exampleProperty
    Integer firstParam
    Integer secondParam
    @JsonProperty("dealsonly")
    Boolean dealsOnly
    @JsonProperty("pgdealsonly")
    Boolean pgDealsOnly
    String siteId
    List<Integer> size
    String sid
}
