package org.prebid.server.bidder.grid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpGridData {

    @JsonProperty("pbadslot")
    String pbAdSlot;

    @JsonProperty("adserver")
    ExtImpGridDataAdServer adServer;
}
