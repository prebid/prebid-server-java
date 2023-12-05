package org.prebid.server.bidder.consumable.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.SupplyChain;
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

    String usPrivacy;

    ConsumableBidGdpr gdpr;

    Boolean coppa;

    SupplyChain sChain;

    Content content;

    String gpp;

    @JsonProperty("gpp_sid")
    List<Integer> gppSid;
}
