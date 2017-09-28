package org.rtb.vexing.adapter;

import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

public interface Adapter {

    /* Default no bid response to optimize the failure case. */
    BidResponse NO_BID_RESPONSE = BidResponse.builder().nbr(0).build();

    Future<BidResponse> clientBid(HttpClient client, Bidder bidder, PreBidRequest request);

    enum Type {
        appnexus, districtm, indexExchange, pubmatic, pulsepoint, rubicon, audienceNetwork, lifestreet
    }
}
