package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum Stage {

    ENTRYPOINT("entrypoint", "entrypoint"),
    RAW_AUCTION_REQUEST("raw-auction-request", "rawauction"),
    PROCESSED_AUCTION_REQUEST("processed-auction-request", "procauction"),
    BIDDER_REQUEST("bidder-request", "bidrequest"),
    RAW_BIDDER_RESPONSE("raw-bidder-response", "rawbidresponse"),
    PROCESSED_BIDDER_RESPONSE("processed-bidder-response", "procbidresponse"),
    ALL_PROCESSED_BID_RESPONSES("all-processed-bid-responses", "allprocbidresponses"),
    AUCTION_RESPONSE("auction-response", "auctionresponse")

    @JsonValue
    final String value
    final String metricValue

    Stage(String value, String metricValue) {
        this.value = value
        this.metricValue = metricValue
    }

    @Override
    String toString() {
        value
    }
}
