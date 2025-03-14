package org.prebid.server.proto.openrtb.ext.request.connatix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpConnatix {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("viewabilityPercentage")
    BigDecimal viewabilityPercentage;

}
