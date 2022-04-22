package org.prebid.server.functional.model.request.amp

import com.fasterxml.jackson.annotation.JsonValue

enum ConsentType {

    TCF_1("1"),
    TCF_2("2"),
    US_PRIVACY("3"),
    BOGUS("4")

    @JsonValue
    final String value

    ConsentType(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
