package org.prebid.server.it.hooks;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

public class SampleItRawAuctionRequestHook implements RawAuctionRequestHook {

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {

        final BidRequest originalBidRequest = auctionRequestPayload.bidRequest();

        final BidRequest updatedBidRequest = updateBidRequest(originalBidRequest);

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                AuctionRequestPayloadImpl.of(payload.bidRequest().toBuilder()
                        .ext(updatedBidRequest.getExt())
                        .build())));
    }

    @Override
    public String code() {
        return "raw-auction-request";
    }

    private BidRequest updateBidRequest(BidRequest originalBidRequest) {
        final boolean shouldUpdate = SampleItModuleUtil.shouldHookUpdateBidRequest(originalBidRequest, code());
        if (!shouldUpdate) {
            return originalBidRequest;
        }

        final ExtRequest originalExt = originalBidRequest.getExt();

        final ObjectNode updatedModuleExt = originalExt
                .getProperty(SampleItModuleUtil.MODULE_EXT)
                .<ObjectNode>deepCopy()
                .put(code() + "-trace", "I've been here");

        final ExtRequest updatedExt = ExtRequest.of(originalExt.getPrebid());
        updatedExt.addProperties(originalExt.getProperties());
        updatedExt.addProperty(SampleItModuleUtil.MODULE_EXT, updatedModuleExt);

        return originalBidRequest.toBuilder()
                .ext(updatedExt)
                .build();
    }
}
