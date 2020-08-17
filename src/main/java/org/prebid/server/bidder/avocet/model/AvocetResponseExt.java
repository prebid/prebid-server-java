package org.prebid.server.bidder.avocet.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AvocetResponseExt {

    @JsonProperty("avocet")
    AvocetBidExtension avocetBidExtension;
}
