package org.prebid.server.bidder.rubicon.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public final class RubiconParams {

    @JsonProperty("accountId")
    Integer accountId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("zoneId")
    Integer zoneId;

    JsonNode inventory;

    JsonNode visitor;

    RubiconVideoParams video;
}
