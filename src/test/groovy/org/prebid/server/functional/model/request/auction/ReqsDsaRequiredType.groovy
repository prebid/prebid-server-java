package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ReqsDsaRequiredType {

    NOT_REQUIRED(0), SUPPORTED(1), REQUIRED(2), REQUIRED_PUBLISHER_ONLINE_PLATFORM(3)

    @JsonValue
    final Integer value

    ReqsDsaRequiredType(Integer value) {
        this.value = value
    }
}
