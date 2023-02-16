package org.prebid.server.auction.model.debug;

import lombok.Value;

@Value(staticConstructor = "of")
public class BidderDebugContext {

    boolean debugEnabled;

    boolean shouldReturnAllBidStatuses;
}
