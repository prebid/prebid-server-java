package org.prebid.server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Describes statically routed endpoints.
 * <p>
 * Note: be aware, admin endpoints have configurable routing.
 */
public enum Endpoint {

    OPENRTB2_AUCTION("/openrtb2/auction"),
    OPENRTB2_AMP("/openrtb2/amp"),
    OPENRTB2_VIDEO("/openrtb2/video"),
    COOKIE_SYNC("/cookie_sync"),
    SETUID("/setuid"),

    BIDDER_PARAMS("/bidders/params"),
    EVENT("/event"),
    GETUIDS("/getuids"),
    INFO_BIDDERS("/info/bidders"),
    OPTOUT("/optout"),
    STATUS("/status"),
    VTRACK("/vtrack");

    @JsonValue
    private final String value;

    Endpoint(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public static Endpoint fromString(String value) {
        return Arrays.stream(values())
                .filter(endpoint -> endpoint.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown endpoint"));
    }
}
