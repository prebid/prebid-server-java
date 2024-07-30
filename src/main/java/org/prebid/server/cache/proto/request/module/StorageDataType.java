package org.prebid.server.cache.proto.request.module;

import com.fasterxml.jackson.annotation.JsonValue;

public enum StorageDataType {

    JSON("json"),
    XML("xml"),
    TEXT("text");

    @JsonValue
    private final String text;

    StorageDataType(String text) {
        this.text = text;
    }
}
