package org.prebid.server.it.hooks;

import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultUtils;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

public class SampleItRejectingBidderRequestHook implements BidderRequestHook {

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(
            BidderRequestPayload bidderRequestPayload, BidderInvocationContext invocationContext) {

        return Future.succeededFuture(InvocationResultUtils.rejected("Rejected by rejecting bidder request hook"));
    }

    @Override
    public String code() {
        return "rejecting-bidder-request";
    }
}
