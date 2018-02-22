package org.prebid.adapter.pubmatic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class PubmaticParams {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("adSlot")
    String adSlot;
}
