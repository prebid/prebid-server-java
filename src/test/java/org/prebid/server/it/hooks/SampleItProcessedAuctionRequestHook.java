package org.prebid.server.it.hooks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

public class SampleItProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private final JacksonMapper mapper;

    public SampleItProcessedAuctionRequestHook(JacksonMapper mapper) {
        this.mapper = mapper;
    }

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
        return "processed-auction-request";
    }

    private BidRequest updateBidRequest(BidRequest originalBidRequest) {
        final ExtRequest originalExt = originalBidRequest.getExt();

        final JsonNode moduleExt = originalExt != null ? originalExt.getProperty(SampleItModule.MODULE_EXT) : null;
        final ObjectNode updatedModuleExt =
                moduleExt != null ? moduleExt.deepCopy() : mapper.mapper().createObjectNode();
        updatedModuleExt.put(code() + "-trace", "I've been here");

        final ExtRequest updatedExt = copyExt(originalExt);
        updatedExt.addProperty(SampleItModule.MODULE_EXT, updatedModuleExt);

        return originalBidRequest.toBuilder()
                .ext(updatedExt)
                .build();
    }

    private ExtRequest copyExt(ExtRequest originalExt) {
        final ExtRequest updatedExt = originalExt != null ? ExtRequest.of(originalExt.getPrebid()) : ExtRequest.empty();
        if (originalExt != null) {
            updatedExt.addProperties(originalExt.getProperties());
        }
        return updatedExt;
    }
}
