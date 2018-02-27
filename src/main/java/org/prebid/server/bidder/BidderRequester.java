package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.execution.GlobalTimeout;

/**
 * Interface for sending requests to Bidders.
 */
public interface BidderRequester {

    /**
     * Returns bidder's name.
     */
    String name();

    /**
     * Executes given request to a bidder.
     */
    Future<BidderSeatBid> requestBids(BidRequest bidRequest, GlobalTimeout timeout);
}
