package org.prebid.server.bidder.appnexus.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Value
public class AppnexusParams {

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
