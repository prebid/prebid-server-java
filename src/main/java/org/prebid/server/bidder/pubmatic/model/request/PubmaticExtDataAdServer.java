package org.prebid.server.bidder.pubmatic.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class PubmaticExtDataAdServer {

    String name;

    @JsonProperty("adslot")
    String adSlot;
}
