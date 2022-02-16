package org.prebid.server.auction.model;

public enum Endpoint {

    OPENRTB2_AUCTION("/openrtb2/auction"),
    OPENRTB2_AMP("/openrtb2/amp"),
    OPENRTB2_VIDEO("/openrtb2/video");

    private final String value;

    Endpoint(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
