package org.rtb.vexing.adapter;

import io.vertx.core.Future;
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.PreBidResponse;

public interface Adapter {

    /* Default no bid response to optimize the failure case. */
    PreBidResponse NO_BID_RESPONSE = PreBidResponse.builder().build();

    Future<Bid> clientBid(Bidder bidder, PreBidRequest request);

    enum Type {
        appnexus, districtm, indexExchange, pubmatic, pulsepoint, rubicon, audienceNetwork, lifestreet
    }
}
