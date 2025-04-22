package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import org.prebid.server.functional.model.bidder.BidderAdapter
import org.prebid.server.functional.model.bidder.BidderName

class Amx implements BidderAdapter {

    @JsonProperty("ct")
    Integer creativeType
    @JsonProperty("startdelay")
    Integer startDelay
    @JsonProperty("ds")
    String demandSource
    @JsonProperty("bc")
    BidderName bidderCode
}
