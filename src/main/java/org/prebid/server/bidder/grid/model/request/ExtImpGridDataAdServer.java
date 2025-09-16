package org.prebid.server.bidder.grid.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGridDataAdServer {

    String name;

    @JsonProperty("adslot")
    String adSlot;
}
