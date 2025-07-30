package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue


enum RuleVariables {

    DEVICE_COUNTRY("deviceCountry"),
    DEVICE_COUNTRY_SET("deviceCountrySet"),
    DATACENTER("datacenters"),
    CHANNEL("channel"),
    BUYER_UID_AVAILABLE("buyeruidAvailable"),
    ANY_ID_AVAILABLE("anyIdAvailable"),
    ANY_ID_OR_USER_FPD_AVAILABLE("anyIdOrUserFpdAvailable"),
    ANY_ID_OR_FPD_AVAIL("anyIdOrFpdAvail"),
    GPP_SID("gppSid"),
    TCF_IN_SCOPE("tcfInScope"),
    PERCENT("percent"),
    DOMAIN("domain"),
    BUNDLE("bundle"),
    MEDIA_TYPES("mediaTypes"),
    AD_UNIT_CODE("adUnitCode"),
    DEVICE_TYPE("deviceType"),
    BID_PRICE("bidPrice"),
    MACRO("macro"),
    INVALID("invalid")

    private String value

    RuleVariables(String value) {
        this.value = value
    }

    @Override
    @JsonValue
    String toString() {
        return value
    }
}
