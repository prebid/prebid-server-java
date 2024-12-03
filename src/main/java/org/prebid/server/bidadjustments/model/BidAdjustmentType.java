package org.prebid.server.bidadjustments.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum BidAdjustmentType {

    CPM, MULTIPLIER, STATIC, UNKNOWN;

    @SuppressWarnings("unused")
    @JsonCreator
    public static BidAdjustmentType of(String name) {
        try {
            return BidAdjustmentType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

}
