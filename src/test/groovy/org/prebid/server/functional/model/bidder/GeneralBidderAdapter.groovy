package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonProperty

class GeneralBidderAdapter extends Generic {

    String siteId
    List<Integer> size
    String sid
    @JsonProperty("ds")
    String demandSource
    @JsonProperty("bc")
    BidderName bidderCode
}
