package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum AnalyticTagStatus {

    NONE, LOOKUP, CONTROL, SUCCESS, ERROR, SUCCESS_ALLOW, SUCCESS_BLOCK, SKIPPED, RUN

    @JsonValue
    String getValue() {
        name().toLowerCase().replace('_', '-')
    }
}
