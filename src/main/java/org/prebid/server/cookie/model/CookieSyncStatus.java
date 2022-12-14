package org.prebid.server.cookie.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CookieSyncStatus {

    OK("ok"), NO_COOKIE("no_cookie");

    private final String value;

    CookieSyncStatus(String status) {
        this.value = status;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
