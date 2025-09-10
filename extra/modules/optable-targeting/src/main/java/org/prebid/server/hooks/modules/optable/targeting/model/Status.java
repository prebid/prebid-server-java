package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Getter;

public enum Status {

    SUCCESS("success"),
    FAIL("fail");

    @Getter
    private final String value;

    Status(String value) {
        this.value = value;
    }
}
