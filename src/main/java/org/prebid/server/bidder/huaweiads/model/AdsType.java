package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Objects;

@Getter
@AllArgsConstructor
public enum AdsType {

    BANNER(8),
    NATIVE(3),
    ROLL(60),
    REWARDED(7),
    SPLASH(1),
    MAGAZINELOCK(2),
    AUDIO(17),
    INTERSTITIAL(12),
    UNKNOWN(Integer.MIN_VALUE);

    private final int type;

    public static AdsType ofTypeName(String typeName) {
        return Arrays.stream(values())
                .filter(value -> value.name().equals(StringUtils.upperCase(typeName)))
                .findFirst()
                .orElse(BANNER);
    }

    public static AdsType ofTypeNumber(Integer typeNumber) {
        return Arrays.stream(values())
                .filter(value -> Objects.equals(value.getType(), typeNumber))
                .findFirst()
                .orElse(UNKNOWN);
    }

}
