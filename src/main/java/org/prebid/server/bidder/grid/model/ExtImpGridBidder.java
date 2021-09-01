package org.prebid.server.bidder.grid.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpGridBidder {

    Integer uid;

    Keywords keywords;
}
