package org.prebid.server.functional.model.deals.lineitem.targeting

import com.fasterxml.jackson.annotation.JsonValue

enum BooleanOperator {

    AND('$and'),
    OR('$or'),
    NOT('$not'),

    INVALID('$invalid'),
    UPPERCASE_AND('$AND')

    @JsonValue
    final String value

    private BooleanOperator(String value) {
        this.value = value
    }
}
