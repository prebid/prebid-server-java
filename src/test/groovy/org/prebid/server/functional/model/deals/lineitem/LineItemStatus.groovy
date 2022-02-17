package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonValue

enum LineItemStatus {

    ACTIVE("active"),
    DELETED("deleted"),
    PAUSED("paused")

    @JsonValue
    final String value

    private LineItemStatus(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
