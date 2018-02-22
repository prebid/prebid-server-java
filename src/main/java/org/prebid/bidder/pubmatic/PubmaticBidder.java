package org.prebid.bidder.pubmatic;

import com.iab.openrtb.request.BidRequest;
import org.prebid.bidder.Bidder;
import org.prebid.bidder.model.BidderBid;
import org.prebid.bidder.model.HttpCall;
import org.prebid.bidder.model.HttpRequest;
import org.prebid.bidder.model.Result;

import java.util.List;

/**
 * Pubmatic implementation.
 * <p>
 * Maintainer email: <a href="mailto:header-bidding@pubmatic.com">header-bidding@pubmatic.com</a>
 */
public class PubmaticBidder implements Bidder {

    public PubmaticBidder() {
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
        return "pubmatic";
    }

    @Override
    public String cookieFamilyName() {
        return "pubmatic";
    }
}
