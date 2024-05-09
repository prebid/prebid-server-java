package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum DsaAdRender {

    ADVERTISER_WONT_RENDER(0),
    ADVERTISER_WILL_RENDER(1)

    @JsonValue
    final int value

    private DsaAdRender(int value) {
        this.value = value
    }
}
