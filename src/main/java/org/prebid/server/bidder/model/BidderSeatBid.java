package org.prebid.server.bidder.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;

import java.util.Collections;
import java.util.List;

/**
 * Seatbid returned by a {@link Bidder}.
 * <p>
 * This is distinct from the {@link com.iab.openrtb.response.SeatBid} so that the prebid-server ext can be passed
 * back with type safety.
 */
@Value
@Builder(toBuilder = true)
public class BidderSeatBid {

    /**
     * List of bids which bidder wishes to make.
     */
    @Builder.Default
    List<BidderBid> bids = Collections.emptyList();

    /**
     * List of debugging info. It should only be populated if the request.test == 1.
     * This will become response.ext.debug.httpcalls.{bidder} on the final OpenRTB response
     */
    @Builder.Default
    List<ExtHttpCall> httpCalls = Collections.emptyList();

    /**
     * List of errors produced by bidder. Errors should describe situations which
     * make the bid (or no-bid) "less than ideal." Common examples include:
     * <p>
     * 1. Connection issues.
     * 2. Imps with Media Types which this Bidder doesn't support.
     * 3. Timeout expired before all expected bids were returned.
     * 4. The Server sent back an unexpected Response, so some bids were ignored.
     * <p>
     * Any errors will be user-facing in the API.
     * Error messages should help publishers understand what might account for "bad" bids.
     */
    @Builder.Default
    List<BidderError> errors = Collections.emptyList();

    /**
     * List of bidder warnings.
     */
    @Builder.Default
    List<BidderError> warnings = Collections.emptyList();

    @Builder.Default
    List<FledgeAuctionConfig> fledgeAuctionConfigs = Collections.emptyList();

    public BidderSeatBid with(List<BidderBid> bids) {
        return toBuilder().bids(bids).build();
    }

    public static BidderSeatBid empty() {
        return BidderSeatBid.builder().build();
    }

    public static BidderSeatBid of(List<BidderBid> bids) {
        return BidderSeatBid.builder()
                .bids(bids)
                .build();
    }
}
