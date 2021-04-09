package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EnforcePurpose {

    no, basic, full;

    @JsonCreator
    public static EnforcePurpose forValue(String value) {
        return EnforcePurpose.valueOf(value);
    }
}
