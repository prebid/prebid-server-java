package org.prebid.server.it.hooks;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

public class SampleItRawAuctionRequestHook implements RawAuctionRequestHook {

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {

        System.out.println(
                "Sample raw-auction hook has been called with " + invocationContext.endpoint() + " endpoint");

        return Future.succeededFuture(InvocationResultImpl.noAction());
    }

    @Override
    public String code() {
        return "raw-auction-request";
    }
}
