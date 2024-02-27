package org.prebid.server.proto.openrtb.ext.request.mgid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpMgid {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("placementId")
    String placementId;

    String cur;

    String currency;

    BigDecimal bidfloor;

    @JsonProperty("bidFloor")
    BigDecimal bidFloorSecond;
}
