package org.prebid.server.bidder.grid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class GridExtImpData {

    @JsonProperty("pbadslot")
    String pbAdSlot;

    @JsonProperty("adserver")
    GridExtImpDataAdServer adServer;
}
