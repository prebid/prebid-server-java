package org.prebid.bidder.index;

import com.iab.openrtb.request.BidRequest;
import org.prebid.bidder.Bidder;
import org.prebid.bidder.model.BidderBid;
import org.prebid.bidder.model.HttpCall;
import org.prebid.bidder.model.HttpRequest;
import org.prebid.bidder.model.Result;

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
