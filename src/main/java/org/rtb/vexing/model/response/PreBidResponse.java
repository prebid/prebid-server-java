package org.rtb.vexing.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public final class PreBidResponse {

    String tid;

    String status;

    List<BidderStatus> bidderStatus;

    List<Bid> bids;

    String burl;
}
