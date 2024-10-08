package org.prebid.server.proto.openrtb.ext.request.bidmatic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpBidmatic {

    @JsonProperty("source")
    String sourceId;

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;
}
