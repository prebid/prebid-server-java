package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidderRequestEnricher;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

import java.util.Set;

public class OptableBidderRequestHook implements BidderRequestHook {

    public static final String CODE = "optable-targeting-bidder-request-hook";

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(BidderRequestPayload bidderRequestPayload,
                                                               BidderInvocationContext invocationContext) {

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        final OptableTargetingProperties properties = moduleContext.getOptableTargetingProperties();
        if (!properties.isPerBidderEnrichmentEnabled()) {
            return noAction(moduleContext);
        }

        final Set<String> biddersToEnrich = moduleContext.getBiddersToEnrich();
        if (CollectionUtils.isEmpty(biddersToEnrich)) {
            return noAction(moduleContext);
        }

        return moduleContext.getOptableTargetingCall()
                .compose(targetingResult ->
                        enrichedPayload(
                                targetingResult, moduleContext, moduleContext.getOptableTargetingProperties()))
                .recover(throwable -> noAction(moduleContext));
    }

    private Future<InvocationResult<BidderRequestPayload>> enrichedPayload(TargetingResult targetingResult,
                                                                            ModuleContext moduleContext,
                                                                            OptableTargetingProperties properties) {

        moduleContext.setTargeting(targetingResult.getAudience());
        moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());

        return update(BidderRequestEnricher.of(targetingResult, properties), moduleContext);
    }

    private Future<InvocationResult<BidderRequestPayload>> noAction(ModuleContext moduleContext) {
        return Future.succeededFuture(
                InvocationResultImpl.<BidderRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .build());
    }

    private static Future<InvocationResult<BidderRequestPayload>> update(
            PayloadUpdate<BidderRequestPayload> payloadUpdate,
            ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<BidderRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(payloadUpdate)
                        .moduleContext(moduleContext)
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
