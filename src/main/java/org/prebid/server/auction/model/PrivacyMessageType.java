package org.prebid.server.auction.model;

public enum PrivacyMessageType {

    ccpa_error("CCPA"),
    coppa_error("COPPA"),
    tcf_error("TCF");

    private final String tag;

    PrivacyMessageType(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }
}
