package org.rtb.vexing.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;

public class ExchangeService {

    /**
     * Executes an OpenRTB v2.5 Auction.
     */
    public Future<BidResponse> holdAuction(BidRequest bidRequest) {
        return Future.succeededFuture();
    }
}
