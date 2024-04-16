package org.prebid.server.bidder.medianet.model.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class MedianetBidResponseExt {

    InterestGroupAuctionIntent igi;
}
