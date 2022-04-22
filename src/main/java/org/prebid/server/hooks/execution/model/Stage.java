package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Stage {

    entrypoint,
    raw_auction_request("raw-auction-request"),
    processed_auction_request("processed-auction-request"),
    bidder_request("bidder-request"),
    raw_bidder_response("raw-bidder-response"),
    processed_bidder_response("processed-bidder-response"),
    auction_response("auction-response");

    @JsonValue
    private final String value;

    Stage() {
        this.value = name();
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
