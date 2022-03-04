package org.prebid.server.floors.model;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class PriceFloorResult {

    String floorRule;

    BigDecimal floorRuleValue;

    BigDecimal floorValue;

    String currency;

    public static PriceFloorResult empty() {
        return PriceFloorResult.of(null, null, null, null);
    }
}
