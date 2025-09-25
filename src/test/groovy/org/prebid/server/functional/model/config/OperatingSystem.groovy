package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum OperatingSystem {

    IOS("ios"),
    ANDROID("android"),
    ROKU("roku"),
    TIZEN("tizen"),
    FIRE("fire")

    @JsonValue
    final String value

    OperatingSystem(String value) {
        this.value = value;
    }
}
