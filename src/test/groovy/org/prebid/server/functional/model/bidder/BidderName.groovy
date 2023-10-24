package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.annotation.JsonValue
import net.minidev.json.annotate.JsonIgnore

enum BidderName {

    BOGUS, ALIAS, GENERIC, APPNEXUS, ACEEX, ACUITYADS, AAX, ADKERNEL, GRID, MEDIANET, OPENX, RUBICON

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }

    @JsonIgnore
    static BidderName bidderNameByString(String bidderName) {
        values().find { bidder -> (bidder.value.equalsIgnoreCase(bidderName)) }
    }
}
