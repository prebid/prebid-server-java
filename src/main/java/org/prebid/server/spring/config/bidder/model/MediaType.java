package org.prebid.server.spring.config.bidder.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MediaType {

    BANNER,
    VIDEO,
    AUDIO,
    NATIVE;

    @JsonValue
    public String getKey() {
        return toString().toLowerCase();
    }

    public static MediaType of(String mediaType) {
        try {
            return valueOf(mediaType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
