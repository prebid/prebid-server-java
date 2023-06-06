package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class CampaignResponseBody {

    @JsonProperty("candidateRetrieval")
    JsonNode candidateRetrieval;

    @JsonProperty("decisions")
    Decisions decisions;
}
