package org.prebid.server.functional.model.deals.lineitem.targeting

import com.fasterxml.jackson.annotation.JsonValue

enum MatchingFunction {

    MATCHES('$matches'),
    IN('$in'),
    INTERSECTS('$intersects'),
    WITHIN('$within')

    @JsonValue
    final String value

    private MatchingFunction(String value) {
        this.value = value
    }
}
