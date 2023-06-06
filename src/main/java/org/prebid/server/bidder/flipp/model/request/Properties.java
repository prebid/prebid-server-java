package org.prebid.server.bidder.flipp.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Properties {

    @JsonProperty("contentCode")
    String contentCode;
}
