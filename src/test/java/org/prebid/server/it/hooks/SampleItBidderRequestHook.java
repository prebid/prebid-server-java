package org.prebid.server.it.hooks;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

import java.util.List;
import java.util.stream.Collectors;

public class SampleItBidderRequestHook implements BidderRequestHook {

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(
            BidderRequestPayload bidderRequestPayload, BidderInvocationContext invocationContext) {

        final BidRequest originalBidRequest = bidderRequestPayload.bidRequest();

        final BidRequest updatedBidRequest = updateBidRequest(originalBidRequest, invocationContext);

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                BidderRequestPayloadImpl.of(payload.bidRequest().toBuilder()
                        .imp(updatedBidRequest.getImp())
                        .build())));
    }

    @Override
    public String code() {
        return "bidder-request";
    }

    private BidRequest updateBidRequest(
            BidRequest originalBidRequest, BidderInvocationContext bidderInvocationContext) {

        final List<Imp> updatedImps = originalBidRequest.getImp().stream()
                .map(imp -> imp.toBuilder().tagid("tagid-from-bidder-request-hook").build())
                .collect(Collectors.toList());

        return originalBidRequest.toBuilder()
                .imp(updatedImps)
                .build();
    }
}
