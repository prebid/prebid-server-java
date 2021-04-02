package org.prebid.server.bidder.avocet.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AvocetBidExtension {

    Integer duration;
}
