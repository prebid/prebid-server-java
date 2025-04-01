package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class TargetingInfo {

    String bidderCode;

    String seat;

    boolean isTargetingEnabled;

    boolean isWinningBid;

    boolean isBidderWinningBid;

    boolean isAddTargetBidderCode;
}
