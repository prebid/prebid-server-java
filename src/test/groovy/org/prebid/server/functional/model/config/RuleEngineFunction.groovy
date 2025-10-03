package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum RuleEngineFunction {

    DEVICE_COUNTRY("deviceCountry", null),
    DEVICE_COUNTRY_IN("deviceCountryIn", "countries"),
    DATA_CENTER("dataCenter", null),
    DATA_CENTER_IN("dataCenterIn", "datacenters"),
    CHANNEL("channel", null),
    EID_AVAILABLE("eidAvailable", null),
    EID_IN("eidIn", "sources"),
    USER_FPD_AVAILABLE("userFpdAvailable", null),
    FPD_AVAILABLE("fpdAvailable", null),
    GPP_SID_AVAILABLE("gppSidAvailable", null),
    GPP_SID_IN("gppSidIn", "sids"),
    TCF_IN_SCOPE("tcfInScope", null),
    PERCENT("percent", "pct"),
    PREBID_KEY("prebidKey", "key"),
    DOMAIN("domain", null),
    DOMAIN_IN("domainIn", "domains"),
    BUNDLE("bundle", null),
    BUNDLE_IN("bundleIn", "bundles"),
    MEDIA_TYPE_IN("mediaTypeIn", "types"),
    AD_UNIT_CODE("adUnitCode",null),
    AD_UNIT_CODE_IN("adUnitCodeIn","codes"),
    DEVICE_TYPE("deviceType",null),
    DEVICE_TYPE_IN("deviceTypeIn","types"),

    private String value
    private String fieldName

    RuleEngineFunction(String value, String fieldName) {
        this.value = value
        this.fieldName = fieldName
    }

    @JsonValue
    @Override
    String toString() {
        return value
    }
}
