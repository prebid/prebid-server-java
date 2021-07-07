package org.prebid.server.it.hooks;

import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.util.stream.Collectors;

public class SampleItAuctionResponseHook implements AuctionResponseHook {

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(
            AuctionResponsePayload auctionResponsePayload, AuctionInvocationContext invocationContext) {

        final BidResponse originalBidResponse = auctionResponsePayload.bidResponse();

        final BidResponse updatedBidResponse = updateBidResponse(originalBidResponse);

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                AuctionResponsePayloadImpl.of(payload.bidResponse().toBuilder()
                        .seatbid(updatedBidResponse.getSeatbid())
                        .build())));
    }

    @Override
    public String code() {
        return "auction-response";
    }

    private BidResponse updateBidResponse(BidResponse originalBidResponse) {
        return originalBidResponse.toBuilder()
                .seatbid(originalBidResponse.getSeatbid().stream()
                        .map(seatBid -> seatBid.toBuilder()
                                .bid(seatBid.getBid().stream()
                                        .map(bid -> bid.toBuilder()
                                                .adm(bid.getAdm()
                                                        + "<Impression><![CDATA["
                                                        + "Auction response hook have been here too]]></Impression>")
                                                .build())
                                        .collect(Collectors.toList()))
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
