package org.prebid.server.bidder.consumable.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ConsumableBidRequest {

    List<ConsumablePlacement> placements;

    Long time;

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("unitId")
    Integer unitId;

    @JsonProperty("unitName")
    String unitName;

    @JsonProperty("includePricingData")
    Boolean includePricingData;

    ConsumableUser user;

    String referrer;

    String ip;

    String url;

    @JsonProperty("enableBotFiltering")
    Boolean enableBotFiltering;

    Boolean parallel;
}
