package org.prebid.server.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public final class PreBidResponse {

    String tid;

    String status;

    List<BidderStatus> bidderStatus;

    List<Bid> bids;

    String burl;
}
