package org.prebid.server.proto.openrtb.ext.request.rubicon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class RubiconVideoParams {

    String language;

    @JsonProperty("playerHeight")
    String playerHeight;

    @JsonProperty("playerWidth")
    String playerWidth;

    Integer sizeId;

    Integer skip;

    Integer skipdelay;
}
