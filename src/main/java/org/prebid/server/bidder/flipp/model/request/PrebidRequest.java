package org.prebid.server.bidder.flipp.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode
public class PrebidRequest {

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("creativeType")
    String creativeType;

    @JsonProperty("publisherNameIdentifier")
    String publisherNameIdentifier;

    Integer height;

    Integer width;
}
