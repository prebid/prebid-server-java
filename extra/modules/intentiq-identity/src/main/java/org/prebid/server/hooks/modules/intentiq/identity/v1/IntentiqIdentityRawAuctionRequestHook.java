package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.intentiq.identity.model.RequestPartnerContext;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.PartnerIdResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

/**
 * Runs at the {@code raw-auction-request} stage, the first stage after the request is parsed. It takes
 * {@code ext.intentiq} out of the request and keeps the requested partner id in the module context, so no
 * later stage, bidder, analytics adapter or debug output ever sees it. It does no I/O, so it cannot reach
 * its timeout.
 */
public class IntentiqIdentityRawAuctionRequestHook implements RawAuctionRequestHook {

    public static final String CODE = "intentiq-identity-raw-auction-request-hook";

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        if (!PartnerIdResolver.hasRequestField(bidRequest)) {
            return Future.succeededFuture(InvocationResultImpl.<AuctionRequestPayload>builder()
                    .status(InvocationStatus.success)
                    .action(InvocationAction.no_action)
                    .moduleContext(invocationContext.moduleContext())
                    .build());
        }

        // A partner id the entrypoint hook read from ext.prebid.intentiq wins over ext.intentiq
        final String partnerId = invocationContext.moduleContext() instanceof RequestPartnerContext early
                && early.partnerId() != null
                ? early.partnerId()
                : PartnerIdResolver.fromRequest(bidRequest);
        return Future.succeededFuture(InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(payload ->
                        AuctionRequestPayloadImpl.of(PartnerIdResolver.withoutRequestField(payload.bidRequest())))
                .moduleContext(new RequestPartnerContext(partnerId))
                .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
