package org.prebid.server.bidder.adtelligent.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.adtelligent.ExtImpAdtelligent;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpAdtelligentBidRequest {

    @JsonProperty("aid")
    Integer sourceId;

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;

    public static ExtImpAdtelligentBidRequest from(Integer sourceId, ExtImpAdtelligent impExt) {
        return ExtImpAdtelligentBidRequest.of(
                sourceId,
                impExt.getPlacementId(),
                impExt.getSiteId(),
                impExt.getBidFloor());
    }
}
