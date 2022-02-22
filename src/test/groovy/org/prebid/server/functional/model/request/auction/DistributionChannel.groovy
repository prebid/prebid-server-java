package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum DistributionChannel {

    SITE, APP

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
