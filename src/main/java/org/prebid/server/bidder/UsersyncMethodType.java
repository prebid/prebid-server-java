package org.prebid.server.bidder;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UsersyncMethodType {

    IFRAME("iframe", UsersyncFormat.BLANK),
    REDIRECT("redirect", UsersyncFormat.PIXEL);

    public final String name;
    public final UsersyncFormat format;

    UsersyncMethodType(String name, UsersyncFormat format) {
        this.name = name;
        this.format = format;
    }

    @JsonValue
    private String getTypeName() {
        return name;
    }
}
