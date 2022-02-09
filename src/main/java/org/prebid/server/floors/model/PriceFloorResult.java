package org.prebid.server.floors.model;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class PriceFloorResult {

    String floorRule;

    BigDecimal floorRuleValue;

    BigDecimal floorValue;

    // TODO: Do we need this field
    String currency;

    public static PriceFloorResult empty() {
        return PriceFloorResult.of(null, null, null, null);
    }
}
