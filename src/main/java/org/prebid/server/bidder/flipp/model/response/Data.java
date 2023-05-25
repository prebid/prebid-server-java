package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class Data {

    @JsonProperty("customData")
    JsonNode customData;

    Integer height;

    Integer width;
}
