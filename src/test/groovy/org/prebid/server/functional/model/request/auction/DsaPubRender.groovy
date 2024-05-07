package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum DsaPubRender {

    PUB_CANT_RENDER(0),
    PUB_MIGHT_RENDER(1),
    PUB_WILL_RENDER(2)

    @JsonValue
    final int value

    private DsaPubRender(int value) {
        this.value = value
    }
}
