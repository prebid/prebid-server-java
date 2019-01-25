package org.prebid.server.bidder.yieldmo.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class YieldmoImpExt {

    @JsonProperty("placement_id")
    String placementId;
}
