package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.AccountControl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.BidRequestEvidenceCollector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.BidRequestPatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.ModuleContextPatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.ModuleContext;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.result.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import io.vertx.core.Future;

public record FiftyOneDeviceDetectionRawAuctionRequestHook(
        AccountControl accountControl,
        BidRequestEvidenceCollector bidRequestEvidenceCollector,
        ModuleContextPatcher moduleContextPatcher,
        BidRequestPatcher bidRequestPatcher
) implements RawAuctionRequestHook {
    private static final String CODE = "fiftyone-devicedetection-raw-auction-request-hook";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload payload,
            AuctionInvocationContext invocationContext)
    {
        if (!accountControl().isAllowed(invocationContext)) {
            return Future.succeededFuture(
                    InvocationResultImpl.<AuctionRequestPayload>builder()
                            .status(InvocationStatus.success)
                            .action(InvocationAction.no_action)
                            .moduleContext(invocationContext.moduleContext())
                            .build());
        }

        final ModuleContext moduleContext = moduleContextPatcher.contextWithNewEvidence(
                (ModuleContext)invocationContext.moduleContext(),
                bidRequestEvidenceCollector.evidenceFrom(payload.bidRequest())
        );

        return  Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(freshPayload -> updatePayload(freshPayload, moduleContext.collectedEvidence()))
                        .moduleContext(moduleContext)
                        .build()
        );
    }

    private AuctionRequestPayload updatePayload(
            AuctionRequestPayload existingPayload,
            CollectedEvidence collectedEvidence)
    {
        final BidRequest currentRequest = existingPayload.bidRequest();
        final BidRequest patchedRequest = bidRequestPatcher.combine(currentRequest, collectedEvidence);
        if (patchedRequest == null || patchedRequest == currentRequest) {
            return existingPayload;
        }
        return AuctionRequestPayloadImpl.of(patchedRequest);
    }
}
