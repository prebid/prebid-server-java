package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonValue

enum Status {

    ACTIVE("active"),
    DELETED("deleted"),
    PAUSED("paused")

    @JsonValue
    final String value

    private Status(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
