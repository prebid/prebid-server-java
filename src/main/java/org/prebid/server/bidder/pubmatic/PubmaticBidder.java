package org.prebid.server.bidder.pubmatic;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderName;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;

import java.util.List;

/**
 * Pubmatic {@link Bidder} implementation.
 * <p>
 * Maintainer email: <a href="mailto:header-bidding@pubmatic.com">header-bidding@pubmatic.com</a>
 */
public class PubmaticBidder implements Bidder {

    private static final String NAME = BidderName.pubmatic.name();

    public PubmaticBidder() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Result<List<HttpRequest>> makeHttpRequests(BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }
}
