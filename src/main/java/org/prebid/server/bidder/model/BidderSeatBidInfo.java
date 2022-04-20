package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class BidderSeatBidInfo {

    List<BidInfo> bidsInfos;

    List<ExtHttpCall> httpCalls;

    List<BidderError> errors;

    List<BidderError> warnings;

    public BidderSeatBidInfo with(List<BidInfo> bids) {
        return BidderSeatBidInfo.of(bids, this.httpCalls, this.errors, this.warnings);
    }
}
