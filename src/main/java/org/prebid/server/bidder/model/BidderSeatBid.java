package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.FledgeConfig;

import java.util.Collections;
import java.util.List;

/**
 * Seatbid returned by a {@link Bidder}.
 * <p>
 * This is distinct from the {@link com.iab.openrtb.response.SeatBid} so that the prebid-server ext can be passed
 * back with type safety.
 */
@Value
@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
public class BidderSeatBid {

    /**
     * List of bids which bidder wishes to make.
     */
    List<BidderBid> bids;

    /**
     * List of debugging info. It should only be populated if the request.test == 1.
     * This will become response.ext.debug.httpcalls.{bidder} on the final OpenRTB response
     */
    List<ExtHttpCall> httpCalls;

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
    List<BidderError> errors;

    /**
     * List of bidder warnings.
     */
    List<BidderError> warnings;

    List<FledgeConfig> fledgeConfigs;

    public BidderSeatBid with(List<BidderBid> bids) {
        return toBuilder().bids(bids).build();
    }

    public static BidderSeatBid empty() {
        return of(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());
    }

    public static BidderSeatBid emptyWithErrors(List<BidderError> errors) {
        return of(Collections.emptyList(), Collections.emptyList(), errors, Collections.emptyList(),
                Collections.emptyList());
    }

    public static BidderSeatBid of(List<BidderBid> bids) {
        return of(bids, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());
    }
}
