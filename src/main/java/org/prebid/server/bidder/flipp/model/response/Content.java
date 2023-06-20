package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Content {

    @JsonProperty("body")
    String body;

    @JsonProperty("customTemplate")
    String customTemplate;

    @JsonProperty("data")
    Data data;

    @JsonProperty("type")
    String type;
}
