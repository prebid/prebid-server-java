package org.prebid.server.bidder;

public enum ViewabilityVendors {

    activeview("doubleclickbygoogle.com"),
    comscore("comscore.com"),
    doubleverify("doubleverify.com"),
    integralads("integralads.com"),
    moat("moat.com"),
    sizemek("sizmek.com"),
    whiteops("whiteops.com");

    private final String url;

    ViewabilityVendors(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
