package org.prebid.server.auction.model;

public enum BidderMessageType {

    multibid(10005, "multibid"),
    invalid_tracking_url_for_vastxml(10006, "invalid tracking URL for vastxml"),
    bidrequest_contains_both_app_and_site(10007, "bidrequest contains both app and site"),
    invalid_price_in_bid(10008, "invalid price in bid");

    private final int code;
    private final String tag;

    BidderMessageType(int code, String tag) {
        this.code = code;
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }
}
