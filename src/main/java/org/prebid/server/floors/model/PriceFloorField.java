package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PriceFloorField {

    @JsonProperty(value = "siteDomain")
    SITE_DOMAIN,

    @JsonProperty(value = "pubDomain")
    PUB_DOMAIN,

    @JsonProperty(value = "domain")
    DOMAIN,

    @JsonProperty(value = "bundle")
    BUNDLE,

    @JsonProperty(value = "channel")
    CHANNEL,

    @JsonProperty(value = "mediaType")
    MEDIA_TYPE,

    @JsonProperty(value = "size")
    SIZE,

    @JsonProperty(value = "gptSlot")
    GPT_SLOT,

    @JsonProperty(value = "pbAdSlot")
    PB_AD_SLOT,

    @JsonProperty(value = "country")
    COUNTRY,

    @JsonProperty(value = "deviceType")
    DEVICE_TYPE


}
