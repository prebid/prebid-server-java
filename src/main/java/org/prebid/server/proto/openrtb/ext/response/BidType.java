package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

public enum BidType {

    @JsonProperty("banner")
    BANNER("banner"),

    @JsonProperty("video")
    VIDEO("video"),

    @JsonProperty("audio")
    AUDIO("audio"),

    @JsonProperty("native")
    X_NATIVE("native");

    private final String value;

    BidType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BidType getEnum(String value) {
        return Arrays.stream(values())
                .filter(type -> type.getValue().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("No enum constant "
                        + "org.prebid.server.proto.openrtb.ext.response.BidType.%s", value)));
    }

    @Override
    public String toString() {
        return this.getValue();
    }
}
