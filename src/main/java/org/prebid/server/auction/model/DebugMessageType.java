package org.prebid.server.auction.model;

public enum DebugMessageType {

    account_level_debug_disabled(10002, "account level debug disabled"),
    bidder_level_debug_disabled(10003, "bidder level debug disabled"),
    incorrect_type_of_first_party_data(10100, "incorrect type of first party data"),
    incorrect_node_field(10101, "incorrect node field");

    private final int code;
    private final String tag;

    DebugMessageType(int code, String tag) {
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
