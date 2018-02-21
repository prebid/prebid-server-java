package org.rtb.vexing.adapter.rubicon.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public final class RubiconVideoParams {

    String language;

    Integer playerHeight;

    Integer playerWidth;

    @JsonProperty("size_id")
    Integer sizeId;

    Integer skip;

    Integer skipdelay;
}
