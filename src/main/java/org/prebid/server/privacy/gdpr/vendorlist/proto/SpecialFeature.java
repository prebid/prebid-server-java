package org.prebid.server.privacy.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum SpecialFeature {

    ONE(1),
    TWO(2),
    UNKNOWN(0);

    private final int code;

    SpecialFeature(int code) {
        this.code = code;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public static SpecialFeature valueOf(int code) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.code == code)
                .findFirst()
                .orElse(UNKNOWN);
    }
}
