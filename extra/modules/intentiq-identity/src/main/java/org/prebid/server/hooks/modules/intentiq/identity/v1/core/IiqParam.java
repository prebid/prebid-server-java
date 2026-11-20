package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

/**
 * Query-parameter names of the IntentIQ S2S HTTP API, shared by both the identity-resolution
 * request and the impression-report request. The enum constant carries the on-the-wire key;
 * the value is request-specific and supplied at the call site (e.g. {@code at} is {@code 39} for the
 * resolution request and {@code 45} for the impression report).
 */
public enum IiqParam {

    // Common / identity-resolution request
    AT("at"),
    MI("mi"),
    DPI("dpi"),
    PT("pt"),
    DPN("dpn"),
    SRVR_REQ("srvrReq"),
    SOURCE("source"),
    IP("ip"),
    IPV6("ipv6"),
    UAS("uas"),
    UH("uh"),
    PCID("pcid"),
    IDTYPE("idtype"),
    REF("ref"),
    IIQUID("iiquid"),
    GDPR("gdpr"),
    US_PRIVACY("us_privacy"),
    GPP("gpp"),
    GPP_SID("gpp_sid"),

    // Impression-report request
    RTYPE("rtype"),
    RDATA("rdata");

    private final String key;

    IiqParam(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
