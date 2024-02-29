package org.prebid.server.functional.model

import com.fasterxml.jackson.annotation.JsonValue

enum ModuleName {

    PB_RICHMEDIA_FILTER("pb-richmedia-filter")

    @JsonValue
    final String code

    ModuleName(String code) {
        this.code = code
    }
}
