package org.prebid.server.bidder.ix;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ix {@link Bidder} implementation.
 */
public class IxBidder implements Bidder {

    @Override
    public Result<List<HttpRequest>> makeHttpRequests(BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
