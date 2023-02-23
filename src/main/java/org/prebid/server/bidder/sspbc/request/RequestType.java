package org.prebid.server.bidder.sspbc.request;

public enum RequestType {

    REQUEST_TYPE_STANDARD(1),
    REQUEST_TYPE_ONE_CODE(2),
    REQUEST_TYPE_TEST(3);

    private final Integer value;

    RequestType(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
