package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpPrebidFloors {

    String floorRule;

    BigDecimal floorRuleValue;

    BigDecimal floorValue;
}
