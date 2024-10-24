package org.prebid.server.it.hooks;

import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultUtils;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

public class SampleItRejectingRawAuctionRequestHook implements RawAuctionRequestHook {

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {

        return Future.succeededFuture(InvocationResultUtils.rejected("Rejected by rejecting raw auction request hook"));
    }

    @Override
    public String code() {
        return "rejecting-raw-auction-request";
    }
}
