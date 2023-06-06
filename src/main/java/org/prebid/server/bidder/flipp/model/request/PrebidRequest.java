package org.prebid.server.bidder.flipp.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class PrebidRequest {

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("creativeType")
    String creativeType;

    @JsonProperty("publisherNameIdentifier")
    String publisherNameIdentifier;

    @JsonProperty("height")
    Integer height;

    @JsonProperty("width")
    Integer width;
}
