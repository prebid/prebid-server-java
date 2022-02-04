package org.prebid.server.auction.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * Describes consent types that can be present in `consent_type` amp query param
 */
public enum ConsentType {

    tcfV1("1"), tcfV2("2"), ccpa("3"), empty(""), unknown("unknown");

    private final String type;

    ConsentType(String type) {
        this.type = type;
    }

    public static ConsentType from(String type) {
        final String resolvedType = StringUtils.defaultString(type);

        return Arrays.stream(ConsentType.values())
                .filter(value -> StringUtils.equals(value.type, resolvedType))
                .findAny()
                .orElse(ConsentType.unknown);
    }
}
