package org.prebid.server.bidder;

public enum UsersyncMethodType {

    IFRAME("iframe", UsersyncFormat.BLINK),
    REDIRECT("redirect", UsersyncFormat.PIXEL);

    public final String name;
    public final UsersyncFormat format;

    UsersyncMethodType(String name, UsersyncFormat format) {
        this.name = name;
        this.format = format;
    }
}
