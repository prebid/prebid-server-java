package org.prebid.server.bidder.huaweiads;

public enum BidTypes {

    BANNER_CODE(8),

    NATIVE_CODE(3),

    ROLL_CODE(60),

    REWARDED_CODE(7),

    SPLASH_CODE(1),

    INTERSTITIAL_CODE(12);

    private final Integer value;

    BidTypes(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return this.value;
    }
}
