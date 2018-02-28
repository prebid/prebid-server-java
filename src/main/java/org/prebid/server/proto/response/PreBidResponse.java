package org.prebid.server.proto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class PreBidResponse {

    String tid;

    String status;

    List<BidderStatus> bidderStatus;

    List<Bid> bids;

    String burl;
}
