package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum IdentifierType {

    EMAIL_ADDRESS("e"),
    PHONE_NUMBER("p"),
    POSTAL_CODE("z"),
    APPLE_IDFA("a"),
    GOOGLE_GAID("g"),
    ROKU_RIDA("r"),
    SAMSUNG_TIFA("s"),
    AMAZON_AFAI("f"),
    NET_ID("n"),
    ID5("id5"),
    UTIQ("utiq"),
    OPTABLE_VID("v")

    @JsonValue
    final String value

    IdentifierType(String value) {
        this.value = value
    }
}
