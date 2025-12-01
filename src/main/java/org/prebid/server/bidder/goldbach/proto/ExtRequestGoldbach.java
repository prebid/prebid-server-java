package org.prebid.server.bidder.goldbach.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value(staticConstructor = "of")
@Builder(toBuilder = true)
public class ExtRequestGoldbach {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("mockResponse")
    Boolean mockResponse;
}
