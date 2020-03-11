package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum EnforcePurpose {
    no("no"), base("base"), full("full");

    private String name;

    EnforcePurpose(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static EnforcePurpose forValue(String value) {
        return EnforcePurpose.valueOf(value);
    }
}
