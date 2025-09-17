package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum RuleEngineFunction {

    DEVICE_COUNTRY("deviceCountry"),
    DEVICE_COUNTRY_IN("deviceCountryIn"),
    DATA_CENTER("dataCenter"),
    DATA_CENTER_IN("dataCenterIn"),
    CHANNEL("channel"),
    EID_AVAILABLE("eidAvailable"),
    EID_IN("eidIn"),
    USER_FPD_AVAILABLE("userFpdAvailable"),
    FPD_AVAILABLE("fpdAvailable"),
    GPP_SID_AVAILABLE("gppSidAvailable"),
    GPP_SID_IN("gppSidIn"),
    TCF_IN_SCOPE("tcfInScope"),
    PERCENT("percent"),
    PREBID_KEY("prebidKey"),
    DOMAIN("domain"),
    DOMAIN_IN("domainIn"),
    BUNDLE("bundle"),
    BUNDLE_IN("bundleIn"),
    MEDIA_TYPE_IN("mediaTypeIn"),
    AD_UNIT_CODE("adUnitCode"),
    AD_UNIT_CODE_IN("adUnitCodeIn"),
    DEVICE_TYPE("deviceType"),
    DEVICE_TYPE_IN("deviceTypeIn"),
    BID_PRICE("bidPrice")

    private String value

    RuleEngineFunction(String value) {
        this.value = value
    }

    @JsonValue
    @Override
    String toString() {
        return value
    }
}
