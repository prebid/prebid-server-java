package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum InvocationStatus {

    SUCCESS, FAILURE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
