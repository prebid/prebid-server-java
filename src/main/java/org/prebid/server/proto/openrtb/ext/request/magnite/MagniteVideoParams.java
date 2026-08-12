package org.prebid.server.proto.openrtb.ext.request.magnite;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class MagniteVideoParams {

    String language;

    @JsonProperty("playerHeight")
    String playerHeight;

    @JsonProperty("playerWidth")
    String playerWidth;

    Integer sizeId;

    Integer skip;

    Integer skipdelay;
}
