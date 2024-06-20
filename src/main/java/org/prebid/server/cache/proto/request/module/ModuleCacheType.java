package org.prebid.server.cache.proto.request.module;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ModuleCacheType {

    JSON("json"),
    XML("xml"),
    TEXT("text");

    @JsonValue
    private final String text;

    ModuleCacheType(final String text) {
        this.text = text;
    }
}
