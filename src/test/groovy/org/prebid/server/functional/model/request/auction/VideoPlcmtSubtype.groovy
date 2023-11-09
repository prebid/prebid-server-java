package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum VideoPlcmtSubtype {

    IN_STREAM(1), ACCOMPANYING_CONTENT(2), INTERSTITIAL(3), NO_CONTENT(4)

    @JsonValue
    final Integer value

    VideoPlcmtSubtype(Integer value) {
        this.value = value
    }
}
