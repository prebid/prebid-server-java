package org.prebid.server.bidder;

public enum UsersyncMethodType {
    IFRAME("iframe", "b"),
    REDIRECT("redirect", "i");

    public final String name;
    public final String format;

    UsersyncMethodType(String name, String format) {
        this.name = name;
        this.format = format;
    }
}
