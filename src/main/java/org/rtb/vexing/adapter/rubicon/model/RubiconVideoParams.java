package org.rtb.vexing.adapter.rubicon.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public final class RubiconVideoParams {

    String language;

    @JsonProperty("playerHeight")
    Integer playerHeight;

    @JsonProperty("playerWidth")
    Integer playerWidth;

    Integer sizeId;

    Integer skip;

    Integer skipdelay;
}
