package org.prebid.server.auction.model;

public enum Endpoint {

    openrtb2_auction("/openrtb2/auction"),
    openrtb2_amp("/openrtb2/amp"),
    openrtb2_video("/openrtb2/video");

    private final String value;

    Endpoint(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
