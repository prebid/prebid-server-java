package org.prebid.server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Endpoint {

    openrtb2_auction("/openrtb2/auction"),
    openrtb2_amp("/openrtb2/amp"),
    openrtb2_video("/openrtb2/video"),
    cookie_sync("/cookie_sync"),
    setuid("/setuid");

    @JsonValue
    private final String value;

    Endpoint(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @JsonCreator
    public static Endpoint fromString(String value) {
        return Arrays.stream(values())
                .filter(endpoint -> endpoint.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown endpoint"));
    }
}
