package org.prebid.server.bidder.consumable.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ConsumablePlacement {

    @JsonProperty("divName")
    String divName;

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("unitId")
    Integer unitId;

    @JsonProperty("unitName")
    String unitName;

    @JsonProperty("adTypes")
    List<Integer> adTypes;
}
