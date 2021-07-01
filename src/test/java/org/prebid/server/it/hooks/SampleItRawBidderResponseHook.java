package org.prebid.server.it.hooks;

import io.vertx.core.Future;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;

import java.util.List;
import java.util.stream.Collectors;

public class SampleItRawBidderResponseHook implements RawBidderResponseHook {

    @Override
    public Future<InvocationResult<BidderResponsePayload>> call(
            BidderResponsePayload bidderResponsePayload, BidderInvocationContext invocationContext) {

        final List<BidderBid> originalBids = bidderResponsePayload.bids();

        final List<BidderBid> updatedBids = updateBids(originalBids);

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                BidderResponsePayloadImpl.of(updatedBids)));
    }

    @Override
    public String code() {
        return "raw-bidder-response";
    }

    private List<BidderBid> updateBids(List<BidderBid> originalBids) {
        return originalBids.stream()
                .map(bidderBid -> BidderBid.of(
                        bidderBid.getBid().toBuilder()
                                .adm(bidderBid.getBid().getAdm()
                                        + "<Impression><![CDATA[Raw bidder response hook have been here]]>"
                                        + "</Impression>")
                                .build(),
                        bidderBid.getType(),
                        bidderBid.getBidCurrency()))
                .collect(Collectors.toList());
    }
}
