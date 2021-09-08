package org.prebid.server.bidder.pubmatic.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class PubmaticExtDataAdServer {

    String name;

    @JsonProperty("adslot")
    String adSlot;
}
