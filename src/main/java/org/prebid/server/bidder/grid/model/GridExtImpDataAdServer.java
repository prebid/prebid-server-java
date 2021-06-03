package org.prebid.server.bidder.grid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class GridExtImpDataAdServer {

    String name;

    @JsonProperty("adslot")
    String adSlot;
}
