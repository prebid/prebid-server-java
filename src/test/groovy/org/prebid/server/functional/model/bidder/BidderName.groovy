package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonValue

enum BidderName {

    GENERIC, RUBICON, APPNEXUS

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
