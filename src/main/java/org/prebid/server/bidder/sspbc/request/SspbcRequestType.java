package org.prebid.server.bidder.sspbc.request;

public enum SspbcRequestType {

    REQUEST_TYPE_STANDARD(1),
    REQUEST_TYPE_ONE_CODE(2),
    REQUEST_TYPE_TEST(3);

    private final Integer value;

    SspbcRequestType(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
