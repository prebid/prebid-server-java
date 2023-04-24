package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AdsType {

    SPLASH(1),
    MAGAZINE_LOCK(2),
    @JsonProperty("native")
    XNATIVE(3),
    REWARDED(7),
    BANNER(8),
    INTERSTITIAL(12),
    AUDIO(17),
    ROLL(60);

    private final Integer code;

    AdsType(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
