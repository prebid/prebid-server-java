package org.prebid.server.hooks.modules.ortb2.blocking.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.Map;
import java.util.Objects;

public class Ortb2BlockingBidderRequestHook implements BidderRequestHook {

    private static final String CODE = "ortb2-blocking-bidder-request";

    private final BidderCatalog bidderCatalog;

    public Ortb2BlockingBidderRequestHook(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(BidderRequestPayload bidderRequestPayload,
                                                               BidderInvocationContext invocationContext) {

        final String bidder = invocationContext.bidder();
        final BidRequest bidRequest = bidderRequestPayload.bidRequest();

        final ModuleContext moduleContext = moduleContext(invocationContext)
                .with(bidder, bidderSupportedOrtbVersion(bidder, aliases(bidRequest)));

        final ExecutionResult<BlockedAttributes> blockedAttributesResult = BlockedAttributesResolver
                .create(
                        bidRequest,
                        bidder,
                        moduleContext.ortbVersionOf(bidder),
                        invocationContext.accountConfig(),
                        invocationContext.debugEnabled())
                .resolve();

        final InvocationResultImpl.InvocationResultImplBuilder<BidderRequestPayload> resultBuilder =
                InvocationResultImpl.<BidderRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(blockedAttributesResult.hasValue()
                                ? InvocationAction.update
                                : InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .warnings(blockedAttributesResult.getWarnings())
                        .errors(blockedAttributesResult.getErrors());

        if (blockedAttributesResult.hasValue()) {
            final BlockedAttributes blockedAttributes = blockedAttributesResult.getValue();
            final RequestUpdater requestUpdater = RequestUpdater.create(blockedAttributes);
            resultBuilder
                    .payloadUpdate(payload -> BidderRequestPayloadImpl.of(requestUpdater.update(payload.bidRequest())))
                    .moduleContext(moduleContext.with(bidder, blockedAttributes));
        }

        return Future.succeededFuture(resultBuilder.build());
    }

    @Override
    public String code() {
        return CODE;
    }

    private static ModuleContext moduleContext(BidderInvocationContext invocationContext) {
        return invocationContext.moduleContext() instanceof ModuleContext moduleContext
                ? moduleContext
                : ModuleContext.create();
    }

    private BidderAliases aliases(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final Map<String, String> aliases = prebid != null ? prebid.getAliases() : null;
        final Map<String, Integer> aliasgvlids = prebid != null ? prebid.getAliasgvlids() : null;

        return BidderAliases.of(aliases, aliasgvlids, bidderCatalog);
    }

    private OrtbVersion bidderSupportedOrtbVersion(String bidder, BidderAliases aliases) {
        return bidderCatalog.bidderInfoByName(aliases.resolveBidder(bidder)).getOrtbVersion();
    }
}
