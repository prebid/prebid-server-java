package org.rtb.vexing.adapter.rubicon.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class RubiconVideoParams {

    String language;

    Integer playerHeight;

    Integer playerWidth;

    @JsonProperty("size_id")
    Integer sizeId;

    Integer skip;

    Integer skipdelay;
}
