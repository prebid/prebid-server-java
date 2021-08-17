package org.prebid.server.auction.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class MultiBidConfig {

    String bidder;

    Integer maxBids;

    String targetBidderCodePrefix;
}
