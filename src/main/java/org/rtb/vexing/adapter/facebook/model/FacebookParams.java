package org.rtb.vexing.adapter.facebook.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class FacebookParams {

    @JsonProperty("placementId")
    String placementId;
}
