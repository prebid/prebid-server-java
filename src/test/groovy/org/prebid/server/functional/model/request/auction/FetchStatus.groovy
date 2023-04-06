package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum FetchStatus {

    NONE, SUCCESS, TIMEOUT, INPROGRESS, ERROR

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
