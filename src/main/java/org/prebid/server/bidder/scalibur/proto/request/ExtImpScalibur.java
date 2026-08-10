package org.prebid.server.bidder.scalibur.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpScalibur {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;

    @JsonProperty("bidfloorcur")
    String bidFloorCur;
}
