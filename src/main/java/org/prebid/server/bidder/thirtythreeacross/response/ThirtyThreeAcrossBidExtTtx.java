package org.prebid.server.bidder.thirtythreeacross.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ThirtyThreeAcrossBidExtTtx {

    @JsonProperty("mediaType")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String mediaType;
}
