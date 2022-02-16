package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Stage {

    ENTRYPOINT,
    RAW_AUCTION_REQUEST("raw-auction-request"),
    PROCESSED_AUCTION_REQUEST("processed-auction-request"),
    BIDDER_REQUEST("bidder-request"),
    RAW_BIDDER_RESPONSE("raw-bidder-response"),
    PROCESSED_BIDDER_RESPONSE("processed-bidder-response"),
    AUCTION_RESPONSE("auction-response");

    @JsonValue
    private final String value;

    Stage() {
        this.value = name().toLowerCase();
    }

    Stage(String value) {
        this.value = value;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public static Stage fromString(String value) {
        return Arrays.stream(values())
                .filter(stage -> stage.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stage"));
    }
}
