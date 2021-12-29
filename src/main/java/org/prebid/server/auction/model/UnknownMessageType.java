package org.prebid.server.auction.model;

public enum UnknownMessageType {

    unknown(10999, "unknown");

    private int code;
    private String tag;

    UnknownMessageType(int code, String tag) {
        this.code = code;
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }
}
