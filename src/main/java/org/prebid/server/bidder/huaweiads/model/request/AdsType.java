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

    public static AdsType fromString(String adtypeLower) {
        return switch (adtypeLower) {
            case "native" -> AdsType.XNATIVE;
            case "rewarded" -> AdsType.REWARDED;
            case "interstitial" -> AdsType.INTERSTITIAL;
            case "roll" -> AdsType.ROLL;
            case "splash" -> AdsType.SPLASH;
            case "magazinelock" -> AdsType.MAGAZINE_LOCK;
            case "audio" -> AdsType.AUDIO;
            default -> AdsType.BANNER;
        };
    }

    public static AdsType fromAdsTypeCode(int adtype) {
        return switch (adtype) {
            case 1 -> AdsType.SPLASH;
            case 2 -> AdsType.MAGAZINE_LOCK;
            case 3 -> AdsType.XNATIVE;
            case 7 -> AdsType.REWARDED;
            case 12 -> AdsType.INTERSTITIAL;
            case 17 -> AdsType.AUDIO;
            case 60 -> AdsType.ROLL;
            default -> AdsType.BANNER;
        };
    }
}
