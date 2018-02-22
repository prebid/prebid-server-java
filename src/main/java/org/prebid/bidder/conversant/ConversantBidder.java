package org.prebid.bidder.conversant;

import com.iab.openrtb.request.BidRequest;
import org.prebid.bidder.Bidder;
import org.prebid.bidder.model.BidderBid;
import org.prebid.bidder.model.HttpCall;
import org.prebid.bidder.model.HttpRequest;
import org.prebid.bidder.model.Result;

import java.util.List;

/**
 * Conversant {@link Bidder} implementation.
 * <p>
 * Maintainer email: <a href="mailto:mediapsr@conversantmedia.com">mediapsr@conversantmedia.com</a>
 */
public class ConversantBidder implements Bidder {

    public ConversantBidder() {
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
        return "conversant";
    }

    @Override
    public String cookieFamilyName() {
        return "conversant";
    }
}
