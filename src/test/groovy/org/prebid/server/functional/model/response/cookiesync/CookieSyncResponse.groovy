package org.prebid.server.functional.model.response.cookiesync

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class CookieSyncResponse {

    Status status
    @JsonProperty("bidder_status")
    List<BidderUsersyncStatus> bidderStatus

    BidderUsersyncStatus getBidderUsersync(BidderName bidderName) {
        bidderStatus?.find { it.bidder == bidderName }
    }

    enum Status {

        OK, NO_COOKIE

        @JsonValue
        String getValue() {
            name().toLowerCase()
        }
    }
}
