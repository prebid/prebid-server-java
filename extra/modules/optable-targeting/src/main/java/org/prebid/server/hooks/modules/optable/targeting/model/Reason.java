package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Getter;

public enum Reason {

    NONE("none"),
    NOBID("nobid"),
    NOKEYWORD("nokeyword");

    @Getter
    private final String value;

    Reason(String value) {
        this.value = value;
    }
}
