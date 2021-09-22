package org.prebid.server.bidder.grid.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGridBidder {

    Integer uid;

    Keywords keywords;
}
