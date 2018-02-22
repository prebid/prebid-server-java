package org.prebid.server.bidder.index;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;

import java.util.List;

/**
 * Index {@link Bidder} implementation.
 * <p>
 * Maintainer email: <a href="mailto:info@prebid.org">info@prebid.org</a>
 */
public class IndexBidder implements Bidder {

    public IndexBidder() {
    }

    @Override
    public Result<List<HttpRequest>> makeHttpRequests(BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String name() {
        return "indexExchange";
    }

    @Override
    public String cookieFamilyName() {
        return "indexExchange";
    }
}
