package org.prebid.server.auction.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * Describes consent types that can be present in `consent_type` amp query param
 */
public enum ConsentType {

    TCF_V1("1"), TCF_V2("2"), CCPA("3"), EMPTY(""), UNKNOWN("unknown");

    private final String type;

    ConsentType(String type) {
        this.type = type;
    }

    public static ConsentType from(String type) {
        final String resolvedValue = StringUtils.defaultString(type);

        return Arrays.stream(ConsentType.values())
                .filter(value -> resolvedValue.equals(value.type))
                .findAny()
                .orElse(ConsentType.UNKNOWN);
    }
}
