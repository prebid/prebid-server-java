package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonValue

enum PeriodType {

    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    CAMPAIGN("campaign")

    @JsonValue
    final String value

    private PeriodType(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
