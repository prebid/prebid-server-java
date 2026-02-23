package org.prebid.server.hooks.modules.optable.targeting.model;

public enum OS {

    IOS("ios"),

    ANDROID("android"),

    ROKU("roku"),

    TIZEN("tizen"),

    FIRE("fire");

    private final String value;

    OS(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
