package org.prebid.server.bidder.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BidderCallType {

    @JsonProperty("http-call")
    HTTP,

    @JsonProperty("stored-bid-response-call")
    STORED_BID_RESPONSE
}

