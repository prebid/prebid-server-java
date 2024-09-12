package org.prebid.server.bidder.adtarget.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.adtarget.ExtImpAdtarget;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpAdtargetBidRequest {

    @JsonProperty("aid")
    Integer sourceId;

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;

    public static ExtImpAdtargetBidRequest from(Integer sourceId, ExtImpAdtarget impExt) {
        return ExtImpAdtargetBidRequest.of(
                sourceId,
                impExt.getPlacementId(),
                impExt.getSiteId(),
                impExt.getBidFloor());
    }
}
