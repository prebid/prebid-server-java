package org.prebid.server.it.hooks;

import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

public class SampleItRejectingProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {

        return Future.succeededFuture(InvocationResultImpl.rejected(
                "Rejected by rejecting processed auction request hook"));
    }

    @Override
    public String code() {
        return "rejecting-processed-auction-request";
    }
}
