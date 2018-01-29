package org.rtb.vexing.bidder.lifestreet;

import com.iab.openrtb.request.BidRequest;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.Result;

import java.util.List;

/**
 * Lifestreet {@link Bidder} implementation.
 * <p>
 * Maintainer email: <a href="mailto:mobile.tech@lifestreet.com">mobile.tech@lifestreet.com</a>
 */
public class LifestreetBidder implements Bidder {

    public LifestreetBidder() {
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
        return "lifestreet";
    }

    @Override
    public String cookieFamilyName() {
        return "lifestreet";
    }
}
