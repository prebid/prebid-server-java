package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum AdjustmentType {

    MULTIPLIER, CPM, STATIC, UNKNOWN

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
