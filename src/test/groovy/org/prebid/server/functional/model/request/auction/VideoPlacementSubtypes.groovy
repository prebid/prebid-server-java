package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum VideoPlacementSubtypes {

    IN_STREAM(1), IN_BANNER(2), IN_ARTICLE(3), IN_FEED(4), SLIDER(5)

    @JsonValue
    final Integer value

    VideoPlacementSubtypes(Integer value) {
        this.value = value
    }
}
