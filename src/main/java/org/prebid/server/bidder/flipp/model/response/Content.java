package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Content {

    String body;

    @JsonProperty("customTemplate")
    String customTemplate;

    Data data;

    String type;
}
