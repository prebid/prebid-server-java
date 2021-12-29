package org.prebid.server.auction.model;

public enum CategoryMappingMessageType {

    account_level_debug_disabled(10002, "account level debug disabled");

    private final int code;
    private final String tag;

    CategoryMappingMessageType(int code, String tag) {
        this.code = code;
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public int getCode() {
        return code;
    }
}
