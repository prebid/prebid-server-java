package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum PriceFloorField {

    SITE_DOMAIN("siteDomain"),
    PUB_DOMAIN("pubDomain"),
    DOMAIN("domain"),
    BUNDLE("bundle"),
    CHANNEL("channel"),
    MEDIA_TYPE("mediaType"),
    SIZE("size"),
    GPT_SLOT("gptSlot"),
    AD_UNIT_CODE("adUnitCode"),
    COUNTRY("country"),
    DEVICE_TYPE("deviceType"),
    BOGUS("bogus")

    @JsonValue
    final String value

    PriceFloorField(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
