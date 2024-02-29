package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum DsaTransparencyParam {

    PROFILING(1),
    BASIC_ADVERTISING(2),
    PRECISE_GEO(3)

    @JsonValue
    final int value

    private DsaTransparencyParam(int value) {
        this.value = value
    }
}
