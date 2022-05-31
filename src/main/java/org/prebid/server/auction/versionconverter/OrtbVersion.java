package org.prebid.server.auction.versionconverter;

import java.util.Arrays;

public enum OrtbVersion {

    ORTB_2_5("2.5"),

    ORTB_2_6("2.6");

    private final String version;

    OrtbVersion(String version) {
        this.version = version;
    }

    public static OrtbVersion fromString(String value) {
        return Arrays.stream(values())
                .filter(ortbVersion -> ortbVersion.version.equals(value))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }
}
