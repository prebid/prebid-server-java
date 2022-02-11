package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonValue

enum RelativePriority {

    VERY_HIGH(1),
    HIGH(2),
    MEDIUM(3),
    LOW(4),
    VERY_LOW(5)

    @JsonValue
    final Integer value

    private RelativePriority(Integer value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
