package org.rtb.vexing.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.PreBidRequestContext;

public interface Adapter {

    Future<BidderResult> requestBids(Bidder bidder, PreBidRequestContext preBidRequestContext);

    String familyName();

    JsonNode usersyncInfo();

    enum Type {
        appnexus, districtm, indexExchange, pubmatic, pulsepoint, rubicon, audienceNetwork, lifestreet
    }
}
