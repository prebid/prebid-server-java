package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum OperationState {

    YES(1), NO(0)

    @JsonValue
    final int value

    OperationState(int value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
