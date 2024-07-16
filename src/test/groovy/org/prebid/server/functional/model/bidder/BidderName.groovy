package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonValue
import net.minidev.json.annotate.JsonIgnore

enum BidderName {

    WILDCARD("*"),
    UNKNOWN("unknown"),
    EMPTY(""),
    BOGUS("bogus"),
    ALIAS("alias"),
    GENERIC_CAMEL_CASE("GeNerIc"),
    GENERIC("generic"),
    RUBICON("rubicon"),
    APPNEXUS("appnexus"),
    RUBICON_ALIAS("rubiconAlias"),
    OPENX("openx"),
    ACEEX("aceex"),
    ACUITYADS("acuityads"),
    AAX("aax"),
    ADKERNEL("adkernel"),
    GRID("grid"),
    MEDIANET("medianet")

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
