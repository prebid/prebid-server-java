package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class TargetingBidInfo {

    BidInfo bidInfo;

    String bidderCode;

    boolean isTargetingEnabled;

    boolean isWinningBid;

    boolean isBidderWinningBid;

    boolean isAddTargetBidderCode;
}
