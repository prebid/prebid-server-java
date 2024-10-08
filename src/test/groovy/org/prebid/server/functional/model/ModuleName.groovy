package org.prebid.server.functional.model

import com.fasterxml.jackson.annotation.JsonValue

enum ModuleName {

    PB_RICHMEDIA_FILTER("pb-richmedia-filter"),
    PB_RESPONSE_CORRECTION ("pb-response-correction"),
    ORTB2_BLOCKING("ortb2-blocking")

    @JsonValue
    final String code

    ModuleName(String code) {
        this.code = code
    }
}
