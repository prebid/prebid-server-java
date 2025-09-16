package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum Fd {

    EXCHANGE(0),
    UPSTREAM_SOURCE(1)

    @JsonValue
    final int value

    Fd(int value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
