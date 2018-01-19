package org.rtb.vexing.bidder;

import com.iab.openrtb.request.BidRequest;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.Result;

import java.util.List;

/**
 * Defines the contract needed to participate in an auction.
 */
public interface Bidder {

    /**
     * Makes the HTTP requests which should be made to fetch bids.
     * <p>
     * The errors should contain a list of errors which explain why this bidder's bids will be "subpar" in some way.
     * For example: the request contained ad types which this bidder doesn't support.
     */
    Result<List<HttpRequest>> makeHttpRequests(BidRequest request);

    /**
     * Unpacks the server's response into bids.
     * <p>
     * The errors should contain a list of errors which explain why this bidder's bids will be
     * "subpar" in some way. For example: the server response didn't have the expected format.
     */
    Result<List<BidderBid>> makeBids(HttpCall httpCall);

    /**
     * Provides a family name by which user ids within bidder's realm are stored in
     * {@link org.rtb.vexing.cookie.UidsCookie}.
     */
    String cookieFamilyName();
}
