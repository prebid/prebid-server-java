package org.prebid.server.bidder.pangle.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
@Value
public class BidExt {

    @JsonProperty("adtype")
    Integer adType;
}
