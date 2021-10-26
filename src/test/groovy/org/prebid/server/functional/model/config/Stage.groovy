package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum Stage {

    ENTRYPOINT("entrypoint"),
    RAW_AUCTION_REQUEST("raw-auction-request"),
    PROCESSED_AUCTION_REQUEST("processed-auction-request"),
    BIDDER_REQUEST("bidder-request"),
    RAW_BIDDER_RESPONSE("raw-bidder-response"),
    PROCESSED_BIDDER_RESPONSE("processed-bidder-response"),
    AUCTION_RESPONSE("auction-response")

    @JsonValue
    final String value

    Stage(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
