package org.prebid.server.it.hooks;

import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultUtils;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook;

public class SampleItRejectingProcessedBidderResponseHook implements ProcessedBidderResponseHook {

    @Override
    public Future<InvocationResult<BidderResponsePayload>> call(
            BidderResponsePayload bidderResponsePayload, BidderInvocationContext invocationContext) {

        return Future.succeededFuture(InvocationResultUtils.rejected(
                "Rejected by rejecting processed bidder response hook"));
    }

    @Override
    public String code() {
        return "rejecting-processed-bidder-response";
    }
}
