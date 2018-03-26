package org.prebid.server.bidder.adform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AdformParams {

    @JsonProperty("mid")
    Long masterTagId;
}
