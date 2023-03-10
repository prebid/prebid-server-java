package org.prebid.server.bidder.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;

import java.util.Collections;
import java.util.List;

/**
 * Composite bidder response (bids + other data) returned by a {@link Bidder}.
 */
@Value
@Builder(toBuilder = true)
public class CompositeBidderResponse {

    @Builder.Default
    List<BidderBid> bids = Collections.emptyList();

    @Builder.Default
    List<BidderError> errors = Collections.emptyList();

    /**
     * FLEDGE interest group bids passback
     */
    List<FledgeAuctionConfig> fledgeAuctionConfigs;

    public static CompositeBidderResponse empty() {
        return builder().build();
    }

    public static CompositeBidderResponse withBids(List<BidderBid> bids,
                                                   List<FledgeAuctionConfig> fledgeAuctionConfigs) {

        return builder()
                .bids(bids)
                .fledgeAuctionConfigs(fledgeAuctionConfigs)
                .build();
    }

    public static CompositeBidderResponse withError(BidderError error) {
        return builder().errors(Collections.singletonList(error)).build();
    }
}
