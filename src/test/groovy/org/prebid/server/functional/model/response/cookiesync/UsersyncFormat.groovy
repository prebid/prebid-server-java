package org.prebid.server.functional.model.response.cookiesync

import com.fasterxml.jackson.annotation.JsonProperty

enum UsersyncFormat {

    @JsonProperty("pixel")
    PIXEL("i"),

    @JsonProperty("blink")
    BLINK("b");

    public final String name

    UsersyncFormat(String name) {
        this.name = name
    }
}
