package org.prebid.server.bidder.medianet.model.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class MedianetBidResponseExt {

    List<InterestGroupAuctionIntent> igi;
}
