package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonValue
import net.minidev.json.annotate.JsonIgnore

enum BidderName {

    WILDCARD("*"),
    UNKNOWN("unknown"),
    EMPTY(""),
    BOGUS("bogus"),
    ALIAS("alias"),
    ALIAS_CAMEL_CASE("AlIaS"),
    GENERIC_CAMEL_CASE("GeNerIc"),
    GENERIC("generic"),
    GENER_X("gener_x"),
    RUBICON("rubicon"),
    APPNEXUS("appnexus"),
    RUBICON_ALIAS("rubiconAlias"),
    OPENX("openx"),
    OPENX_ALIAS("openxalias"),
    ACEEX("aceex"),
    ACUITYADS("acuityads"),
    AAX("aax"),
    ADKERNEL("adkernel"),
    IX("ix"),
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
