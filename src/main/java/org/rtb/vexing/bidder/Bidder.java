package org.rtb.vexing.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.rtb.vexing.bidder.model.BidderSeatBid;

public interface Bidder {

    Future<BidderSeatBid> requestBids(BidRequest bidRequest);
}
