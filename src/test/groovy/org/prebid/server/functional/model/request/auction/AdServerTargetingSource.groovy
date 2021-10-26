package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum AdServerTargetingSource {

    BIDREQUEST, STATIC, BIDRESPONSE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
