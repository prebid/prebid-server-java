package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.execution.Timeout;

import java.math.BigDecimal;

/**
 * Interface for sending requests to Bidders.
 */
public interface BidderRequester {

    /**
     * Executes given request to a bidder.
     */
    Future<BidderSeatBid> requestBids(BidRequest bidRequest, Timeout timeout, BigDecimal bidPriceAdjustmentFactor);
}
