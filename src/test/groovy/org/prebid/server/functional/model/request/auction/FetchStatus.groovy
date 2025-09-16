package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum FetchStatus {

    NONE, SUCCESS, TIMEOUT, INPROGRESS, ERROR, SUCCESS_ALLOW, SUCCESS_BLOCK, SKIPPED, RUN

    @JsonValue
    String getValue() {
        name().toLowerCase().replace('_', '-')
    }
}
