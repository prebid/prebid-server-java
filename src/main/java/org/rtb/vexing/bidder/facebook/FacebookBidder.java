package org.rtb.vexing.bidder.facebook;

import com.iab.openrtb.request.BidRequest;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.Result;

import java.util.List;

/**
 * Facebook {@link Bidder} implementation.
 * <p>
 * Maintainer email: <a href="mailto:info@prebid.org">info@prebid.org</a>
 */
public class FacebookBidder implements Bidder {

    public FacebookBidder() {
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
        return "audienceNetwork";
    }

    @Override
    public String cookieFamilyName() {
        return "audienceNetwork";
    }
}
