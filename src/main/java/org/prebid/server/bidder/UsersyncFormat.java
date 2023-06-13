package org.prebid.server.bidder;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum UsersyncFormat {

    @JsonProperty("pixel")
    PIXEL("i"),

    @JsonProperty("blank")
    BLANK("b");

    public final String name;

    UsersyncFormat(String name) {
        this.name = name;
    }
}
