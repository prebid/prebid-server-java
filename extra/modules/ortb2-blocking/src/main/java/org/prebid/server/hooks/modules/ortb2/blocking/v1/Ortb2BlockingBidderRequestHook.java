package org.prebid.server.hooks.modules.ortb2.blocking.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.modules.ortb2.blocking.core.BlockedAttributesResolver;
import org.prebid.server.hooks.modules.ortb2.blocking.core.RequestUpdater;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.ExecutionResult;
import org.prebid.server.hooks.modules.ortb2.blocking.model.ModuleContext;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.BidderRequestPayloadImpl;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

public class Ortb2BlockingBidderRequestHook implements BidderRequestHook {

    private static final String CODE = "ortb2-blocking-bidder-request";

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(
        BidderRequestPayload bidderRequestPayload,
        BidderInvocationContext invocationContext) {

        final ExecutionResult<BlockedAttributes> blockedAttributesResult =
            BlockedAttributesResolver
                .create(
                    bidderRequestPayload.bidRequest(),
                    invocationContext.bidder(),
                    invocationContext.accountConfig(),
                    invocationContext.debugEnabled())
                .resolve();

        final InvocationResultImpl.InvocationResultImplBuilder<BidderRequestPayload> resultBuilder =
            InvocationResultImpl.<BidderRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(blockedAttributesResult.hasValue()
                    ? InvocationAction.update
                    : InvocationAction.no_action)
                .warnings(blockedAttributesResult.getWarnings())
                .errors(blockedAttributesResult.getErrors());
        if (blockedAttributesResult.hasValue()) {
            final BlockedAttributes blockedAttributes = blockedAttributesResult.getValue();
            final RequestUpdater requestUpdater = RequestUpdater.create(blockedAttributes);
            resultBuilder
                .payloadUpdate(payload ->
                    BidderRequestPayloadImpl.of(requestUpdater.update(payload.bidRequest())))
                .moduleContext(moduleContext(invocationContext, blockedAttributes));
        }

        return Future.succeededFuture(resultBuilder.build());
    }

    @Override
    public String code() {
        return CODE;
    }

    private static ModuleContext moduleContext(
        BidderInvocationContext invocationContext, BlockedAttributes blockedAttributes) {

        final Object moduleContext = invocationContext.moduleContext();
        final String bidder = invocationContext.bidder();

        return moduleContext instanceof ModuleContext
            ? ((ModuleContext) moduleContext).with(bidder, blockedAttributes)
            : ModuleContext.create(bidder, blockedAttributes);
    }
}
