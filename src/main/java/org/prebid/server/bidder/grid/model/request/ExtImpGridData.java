package org.prebid.server.bidder.grid.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGridData {

    @JsonProperty("pbadslot")
    String pbAdSlot;

    @JsonProperty("adserver")
    ExtImpGridDataAdServer adServer;
}
