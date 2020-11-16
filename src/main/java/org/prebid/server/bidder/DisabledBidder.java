package org.prebid.server.bidder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Used to indicate disabled bidder. First method call to this bidder should return empty bids and error in result.
 */
public class DisabledBidder implements Bidder<Void> {

    private String errorMessage;

    public DisabledBidder(String errorMessage) {
        this.errorMessage = Objects.requireNonNull(errorMessage);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        return Result.withError(BidderError.badInput(errorMessage));
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        throw new UnsupportedOperationException();
    }
}
