package org.prebid.server.bidder.grid.model.responce;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

@Builder
@Value
public class GridBidderBid {

    GridBid bid;

    BidType type;

    String bidCurrency;

    Integer dealPriority;

    ExtBidPrebidVideo videoInfo;

    public static GridBidderBid of(GridBid bid, BidType bidType, String bidCurrency) {
        return GridBidderBid.builder()
                .bid(bid)
                .type(bidType)
                .bidCurrency(bidCurrency)
                .build();
    }

}
