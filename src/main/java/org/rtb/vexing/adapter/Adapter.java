package org.rtb.vexing.adapter;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.PreBidResponse;

public interface Adapter {

    /* Default no bid response to optimize the failure case. */
    PreBidResponse NO_BID_RESPONSE = PreBidResponse.builder().build();

    Future<BidderResult> requestBids(Bidder bidder, PreBidRequest preBidRequest, HttpServerRequest httpRequest);

    enum Type {
        appnexus, districtm, indexExchange, pubmatic, pulsepoint, rubicon, audienceNetwork, lifestreet
    }
}
