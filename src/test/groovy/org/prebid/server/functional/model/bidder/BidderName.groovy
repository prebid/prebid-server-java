package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonValue

enum BidderName {

    GENERIC_ALIAS, GENERIC, RUBICON, APPNEXUS, BOGUS

    @JsonValue
    String getValue() {
        name() == "GENERIC_ALIAS" ? "genericAlias" : name().toLowerCase()
    }
}
