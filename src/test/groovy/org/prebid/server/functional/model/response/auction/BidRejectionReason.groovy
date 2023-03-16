package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum BidRejectionReason {

    NO_BID(0),
    TIMED_OUT(101),
    REJECTED_BY_HOOK(200),
    REJECTED_BY_PRIVACY(202),
    REJECTED_BY_MEDIA_TYPE(204),
    REJECTED_DUE_TO_PRICE_FLOOR(301),
    OTHER_ERROR(100)

    @JsonValue
    final Integer code

    BidRejectionReason(Integer value) {
        this.code = value
    }
}
