package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonProperty

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
