package org.prebid.server.bidder;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum UsersyncFormat {

    @JsonProperty("pixel")
    PIXEL("i"),

    @JsonProperty("blink")
    BLINK("b");

    public final String name;

    UsersyncFormat(String name) {
        this.name = name;
    }
}
