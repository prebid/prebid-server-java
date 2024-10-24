package org.prebid.server.it.hooks;

import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultUtils;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;

public class SampleItRejectingRawBidderResponseHook implements RawBidderResponseHook {

    @Override
    public Future<InvocationResult<BidderResponsePayload>> call(
            BidderResponsePayload bidderResponsePayload, BidderInvocationContext invocationContext) {

        return Future.succeededFuture(InvocationResultUtils.rejected("Rejected by rejecting raw bidder response hook"));
    }

    @Override
    public String code() {
        return "rejecting-raw-bidder-response";
    }
}
