package org.prebid.server.proto.openrtb.ext.request.appnexus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.appnexus.model.AppnexusKeyVal;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Value
public final class ExtImpAppnexus {

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("invCode")
    String invCode;

    String member;

    List<AppnexusKeyVal> keywords;

    @JsonProperty("trafficSourceCode")
    String trafficSourceCode;

    BigDecimal reserve;

    String position;
}
