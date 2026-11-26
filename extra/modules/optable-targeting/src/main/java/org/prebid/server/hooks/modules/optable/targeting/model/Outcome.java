package org.prebid.server.hooks.modules.optable.targeting.model;

public enum Outcome {

    ENRICHED("enriched"),
    NO_DATA("no-data"),
    TIMEOUT("timeout"),
    ERROR("error");

    final String value;

    Outcome(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
