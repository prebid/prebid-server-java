package org.prebid.server.bidder.conversant;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;

import java.util.List;

/**
 * Conversant {@link Bidder} implementation.
 * <p>
 * Maintainer email: <a href="mailto:mediapsr@conversantmedia.com">mediapsr@conversantmedia.com</a>
 */
public class ConversantBidder implements Bidder {

    @Override
    public Result<List<HttpRequest>> makeHttpRequests(BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        throw new UnsupportedOperationException();
    }
}
