package org.prebid.server.auction.model;

/**
 * Describes consent types that can be present in `consent_type` amp query param
 */
public enum ConsentType {

    TCF_V_1("tcfV1"),
    TCF_V_2("tcfV2"),
    US_PRIVACY("usPrivacy"),
    UNKNOWN("unknown");

    private final String name;

    ConsentType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
