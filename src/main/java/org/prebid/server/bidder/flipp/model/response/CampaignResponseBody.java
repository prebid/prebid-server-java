package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class CampaignResponseBody {

    JsonNode candidateRetrieval;

    Decisions decisions;
}
