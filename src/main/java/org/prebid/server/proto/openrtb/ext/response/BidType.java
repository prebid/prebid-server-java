package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public enum BidType {

    BANNER,
    VIDEO,
    AUDIO,
    X_NATIVE("native");

    private final String value;

    BidType() {
        this.value = name().toLowerCase();
    }

    BidType(String value) {
        this.value = value;
    }

    public static BidType fromString(String bidType) {
        try {
            return StringUtils.equalsIgnoreCase(bidType, "native") ? X_NATIVE : valueOf(bidType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static BidType getEnum(String value) {
        return Arrays.stream(values())
                .filter(type -> type.getValue().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("No enum constant "
                        + "org.prebid.server.proto.openrtb.ext.response.BidType.%s", value)));
    }

    public String getValue() {
        return value;
    }

    @JsonValue
    @Override
    public String toString() {
        return this == X_NATIVE ? "native" : getValue();
    }
}
