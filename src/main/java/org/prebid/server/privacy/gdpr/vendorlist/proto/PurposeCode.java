package org.prebid.server.privacy.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PurposeCode {

    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),
    UNKNOWN(0);

    @JsonValue
    private final int code;

    PurposeCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public static PurposeCode valueOf(int code) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.code == code)
                .findFirst()
                .orElse(UNKNOWN);
    }
}
