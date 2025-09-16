package org.prebid.server.functional.model.request

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum Format {

    IMAGE("i"), BLANK("b")

    @JsonValue
    final String value

    Format(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
