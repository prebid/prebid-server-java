package org.prebid.server.bidder.medianet.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class InterestGroupAuctionIntent {

    List<InterestGroupAuctionBuyer> igb;

    List<InterestGroupAuctionSeller> igs;
}
