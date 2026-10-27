package org.prebid.server.bidder.goldbach.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtRequestGoldbach {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("mockResponse")
    Boolean mockResponse;
}
