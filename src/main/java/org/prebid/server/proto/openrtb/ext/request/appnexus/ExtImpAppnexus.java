package org.prebid.server.proto.openrtb.ext.request.appnexus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtImpAppnexus {

    @JsonProperty("placementId")
    Integer legacyPlacementId;

    @JsonProperty("invCode")
    String legacyInvCode;

    @JsonProperty("trafficSourceCode")
    String legacyTrafficSourceCode;

    @JsonProperty("placement_id")
    Integer placementId;

    @JsonProperty("inv_code")
    String invCode;

    String member;

    List<AppnexusKeyVal> keywords;

    @JsonProperty("traffic_source_code")
    String trafficSourceCode;

    BigDecimal reserve;

    String position;

    Boolean usePmtRule;

    Boolean generateAdPodId;

    ObjectNode privateSizes; // At this time we do no processing on the private sizes, so just leaving it as a JSON blob
}
