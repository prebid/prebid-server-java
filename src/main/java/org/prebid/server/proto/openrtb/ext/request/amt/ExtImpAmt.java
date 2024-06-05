package org.prebid.server.proto.openrtb.ext.request.amt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
@Builder(toBuilder = true)
public class ExtImpAmt {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;

    @JsonProperty("bidCeiling")
    BigDecimal bidCeiling;

}
