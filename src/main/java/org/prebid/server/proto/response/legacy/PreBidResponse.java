package org.prebid.server.proto.response.legacy;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Deprecated
@Builder
@Value
public class PreBidResponse {

    String tid;

    String status;

    List<BidderStatus> bidderStatus;

    List<Bid> bids;

    String burl;
}
