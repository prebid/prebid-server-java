package org.prebid.server.bidder;

public enum ViewabilityVendors {

    ACTIVEVIEW("doubleclickbygoogle.com"),
    COMSCORE("comscore.com"),
    DOUBLEVERIFY("doubleverify.com"),
    INTEGRALADS("integralads.com"),
    MOAT("moat.com"),
    SIZEMEK("sizmek.com"),
    WHITEOPS("whiteops.com");

    private final String url;

    ViewabilityVendors(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
