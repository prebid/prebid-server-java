package org.prebid.server.bidder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Defines the contract needed to participate in an auction.
 */
public interface Bidder<T> {

    /**
     * Makes the HTTP requests which should be made to fetch bids.
     * <p>
     * The errors should contain a list of errors which explain why this bidder's bids will be "subpar" in some way.
     * For example: the request contained ad types which this bidder doesn't support.
     */
    Result<List<HttpRequest<T>>> makeHttpRequests(BidRequest request);

    /**
     * Unpacks the server's response into bids.
     * <p>
     * The errors should contain a list of errors which explain why this bidder's bids will be
     * "subpar" in some way. For example: the server response didn't have the expected format.
     */
    Result<List<BidderBid>> makeBids(BidderCall<T> httpCall, BidRequest bidRequest);

    /**
     * Compound Bidder response with bids and other data to be passed back.
     * <p>
     * The errors should contain a list of errors which explain why this bidder's bids will be
     * "subpar" in some way. For example: the server response didn't have the expected format.
     */
    default CompositeBidderResponse makeBidderResponse(BidderCall<T> httpCall, BidRequest bidRequest) {
        final var result = makeBids(httpCall, bidRequest);
        return result != null
                ? CompositeBidderResponse.builder()
                    .bids(result.getValue())
                    .errors(result.getErrors())
                    .build()
                : null;
    }

    /**
     * Extracts targeting from bidder-specific extension. It is safe to assume that {@code ext} is not null.
     */
    default Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    /**
     * This method is much the same as {@link #makeHttpRequests}, except it is fed the bidder request
     * that timed out, and expects that only one notification "request" will be generated. A use case for multiple
     * timeout notifications has not been anticipated.
     * <p>
     * Do note that if {@link #makeHttpRequests} returns multiple requests, and more than one of these times out,
     * this method will be called once for each timed out request.
     */
    default HttpRequest<Void> makeTimeoutNotification(HttpRequest<T> httpRequest) {
        return null;
    }
}
