package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum SourceType {

    UNKNOWN(0), MEASUREMENT_VENDOR_PROVIDED(1), PUBLISHER_PROVIDED(2), EXCHANGER_PROVIDED(3)

    @JsonValue
    final Integer value

    SourceType(Integer value) {
        this.value = value
    }
}
