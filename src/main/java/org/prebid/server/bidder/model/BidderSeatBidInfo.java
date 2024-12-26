package org.prebid.server.bidder.model;

import lombok.Value;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtIgi;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;

import java.util.List;

@Value(staticConstructor = "of")
public class BidderSeatBidInfo {

    List<BidInfo> bidsInfos;

    List<ExtHttpCall> httpCalls;

    List<BidderError> errors;

    List<BidderError> warnings;

    @Deprecated(forRemoval = true)
    List<FledgeAuctionConfig> fledgeAuctionConfigs;

    List<ExtIgi> igi;

    public BidderSeatBidInfo with(List<BidInfo> bids) {
        return BidderSeatBidInfo.of(
                bids,
                this.httpCalls,
                this.errors,
                this.warnings,
                this.fledgeAuctionConfigs,
                this.igi);
    }
}
