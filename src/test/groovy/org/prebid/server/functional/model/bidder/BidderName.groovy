package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonValue
import net.minidev.json.annotate.JsonIgnore

enum BidderName {

    ALIAS("alias"),
    GENERIC("generic"),
    GENERIC_CAMEL_CASE("GeNerIc"),
    RUBICON("rubicon"),
    APPNEXUS("appnexus"),
    RUBICON_ALIAS("rubiconAlias"),
    BOGUS("bogus"),
    OPENX("openx"),
    UNKNOWN("unknown")


    @JsonValue
    final String value

    BidderName(String value) {
        this.value = value
    }

    String toString() {
        value
    }

    @JsonIgnore
    static BidderName bidderNameByString(String bidderName) {
        values().find { bidder -> (bidder.value.equalsIgnoreCase(bidderName)) }
    }
}
