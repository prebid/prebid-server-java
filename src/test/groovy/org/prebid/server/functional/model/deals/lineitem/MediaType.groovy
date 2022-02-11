package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonValue

enum MediaType {

    BANNER("banner")

    @JsonValue
    final String value

    private MediaType(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
