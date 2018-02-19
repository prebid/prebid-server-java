package org.rtb.vexing.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.rtb.vexing.bidder.model.BidderSeatBid;
import org.rtb.vexing.execution.GlobalTimeout;

/**
 * Interface for sending requests to Bidders.
 */
public interface BidderRequester {

    /**
     * Executes given request to a bidder.
     */
    Future<BidderSeatBid> requestBids(BidRequest bidRequest, GlobalTimeout timeout);

    /**
     * Returns bidder's cookie family name.
     */
    String cookieFamilyName();

    /**
     * Returns bidder's name.
     */
    String name();
}
