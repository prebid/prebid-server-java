package org.prebid.server.functional.model.response

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum BidderErrorCode {

    TIMEOUT(1),
    BAD_INPUT(2),
    BAD_SERVER_RESPONSE(3),
    FAILED_TO_REQUEST_BIDS(4),
    INVALID_BID(5),
    REJECTED_IPF(6),
    GENERIC(999)

    @JsonValue
    final Integer value

    BidderErrorCode(Integer value) {
        this.value = value
    }
}
