package org.prebid.server.bidder.avocet.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AvocetBidExtension {

    Integer duration;

    @JsonProperty("deal_priority")
    Integer dealPriority;
}
