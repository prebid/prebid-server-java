package org.prebid.server.proto.openrtb.ext.request.orbidder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpOrbidder {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;
}
