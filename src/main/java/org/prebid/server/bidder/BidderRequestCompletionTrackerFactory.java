package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;

public interface BidderRequestCompletionTrackerFactory {

    BidderRequestCompletionTracker create(BidRequest bidRequest);
}
