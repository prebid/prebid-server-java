package org.prebid.server.bidadjustments.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public enum BidAdjustmentType {

    CPM, MULTIPLIER, STATIC, UNKNOWN;

    @SuppressWarnings("unused")
    @JsonCreator
    public static BidAdjustmentType of(String name) {
        return Arrays.stream(values())
                .filter(type -> StringUtils.equalsIgnoreCase(type.name(), name))
                .findFirst()
                .orElse(UNKNOWN);
    }

}
