package org.prebid.server.proto.openrtb.ext.request.adtarget;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpAdtarget {

    @JsonProperty("aid")
    String sourceId;

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;
}
