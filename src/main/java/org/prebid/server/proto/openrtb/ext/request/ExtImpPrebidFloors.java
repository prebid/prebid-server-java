package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpPrebidFloors {

    @JsonProperty("floorRule")
    String floorRule;

    @JsonProperty("floorRuleValue")
    BigDecimal floorRuleValue;

    @JsonProperty("floorValue")
    BigDecimal floorValue;
}
