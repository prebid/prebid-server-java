package org.rtb.vexing.bidder.conversant;

import com.iab.openrtb.request.BidRequest;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.Result;

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
