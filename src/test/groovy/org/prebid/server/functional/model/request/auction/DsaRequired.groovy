package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum DsaRequired {

    NOT_REQUIRED(0),
    SUPPORTED(1),
    REQUIRED(2),
    REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM(3)

    @JsonValue
    final int value

    private DsaRequired(int value) {
        this.value = value
    }
}
