package org.prebid.server.functional.model.response.cookiesync

import com.fasterxml.jackson.annotation.JsonValue

enum UsersyncFormat {

    PIXEL("i"),
    BLANK("b");

    public final String name;

    UsersyncFormat(String name) {
        this.name = name;
    }

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
