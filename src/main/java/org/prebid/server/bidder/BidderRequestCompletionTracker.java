package org.prebid.server.bidder;

import io.vertx.core.Future;
import org.prebid.server.bidder.model.BidderBid;

import java.util.List;

public interface BidderRequestCompletionTracker {

    Future<Void> future();

    void processBids(List<BidderBid> bids);
}
