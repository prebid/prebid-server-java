package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty

enum BidderCallType {

    @JsonProperty("http-call")
    HTTP,

    @JsonProperty("stored-bid-response-call")
    STORED_BID_RESPONSE
}
