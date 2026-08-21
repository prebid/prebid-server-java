package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum MergeRule {

    REPLACE, CONCAT

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
