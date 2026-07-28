package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidderRequestEnricher;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.Id5Resolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class OptableBidderRequestHook implements BidderRequestHook {

    public static final String CODE = "optable-targeting-bidder-request-hook";

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(BidderRequestPayload bidderRequestPayload,
                                                               BidderInvocationContext invocationContext) {

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        final OptableTargetingProperties properties = moduleContext.getOptableTargetingProperties();

        final Set<String> biddersToEnrich = moduleContext.getBiddersToEnrich();
        if (CollectionUtils.isEmpty(biddersToEnrich)
                || !biddersToEnrich.contains(invocationContext.bidder())) {
            return noAction(moduleContext, null);
        }

        final String bidder = invocationContext.bidder();

        return moduleContext.getOptableTargetingCall()
                .compose(targetingResult ->
                        enrichedPayload(targetingResult, moduleContext, properties, bidder))
                .recover(throwable -> failedAction(moduleContext, throwable, bidder));
    }

    private Future<InvocationResult<BidderRequestPayload>> enrichedPayload(TargetingResult targetingResult,
                                                                            ModuleContext moduleContext,
                                                                            OptableTargetingProperties properties,
                                                                            String bidder) {

        final long executionTime = calcExecutionTime(moduleContext);
        final boolean hasData = hasEnrichmentData(targetingResult);

        if (hasData) {
            moduleContext.setTargeting(targetingResult.getAudience());
            moduleContext.setId5Signature(Id5Resolver.resolveId5Signature(targetingResult));
            moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());
        }

        final String outcome = hasData ? "enriched" : "no-data";
        final Tags analyticsTags = AnalyticTagsResolver.toBidderEnrichRequestAnalyticTags(
                bidder, outcome, executionTime);

        return hasData
                ? update(BidderRequestEnricher.of(targetingResult, properties), moduleContext, analyticsTags)
                : noAction(moduleContext, analyticsTags);
    }

    private Future<InvocationResult<BidderRequestPayload>> failedAction(ModuleContext moduleContext,
                                                                         Throwable throwable,
                                                                         String bidder) {

        final long executionTime = calcExecutionTime(moduleContext);
        final String outcome = throwable instanceof TimeoutException ? "timeout" : "error";
        final Tags analyticsTags = AnalyticTagsResolver.toBidderEnrichRequestAnalyticTags(
                bidder, outcome, executionTime);

        return noActionResponse(moduleContext, analyticsTags);
    }

    private static boolean hasEnrichmentData(TargetingResult targetingResult) {
        return Optional.ofNullable(targetingResult)
                .map(TargetingResult::getOrtb2)
                .map(Ortb2::getUser)
                .isPresent();
    }

    private static long calcExecutionTime(ModuleContext moduleContext) {
        final long startTime = moduleContext.getCallTargetingAPITimestamp();
        return startTime > 0 ? System.currentTimeMillis() - startTime : 0;
    }

    private Future<InvocationResult<BidderRequestPayload>> noAction(ModuleContext moduleContext, Tags analyticsTags) {
        final Future<TargetingResult> targetingCall = moduleContext.getOptableTargetingCall();
        if (targetingCall != null) {
            return targetingCall.compose(targetingResult -> {
                moduleContext.setId5Signature(Id5Resolver.resolveId5Signature(targetingResult));

                return Future.succeededFuture(
                        InvocationResultImpl.<BidderRequestPayload>builder()
                                .status(InvocationStatus.success)
                                .action(InvocationAction.no_action)
                                .analyticsTags(analyticsTags)
                                .moduleContext(moduleContext)
                                .build());
            });
        }

        return noActionResponse(moduleContext, analyticsTags);
    }

    private Future<InvocationResult<BidderRequestPayload>> noActionResponse(ModuleContext moduleContext,
                                                                            Tags analyticsTags) {

        return Future.succeededFuture(
                InvocationResultImpl.<BidderRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .analyticsTags(analyticsTags)
                        .moduleContext(moduleContext)
                        .build());
    }

    private static Future<InvocationResult<BidderRequestPayload>> update(
            PayloadUpdate<BidderRequestPayload> payloadUpdate,
            ModuleContext moduleContext,
            Tags analyticsTags) {

        return Future.succeededFuture(
                InvocationResultImpl.<BidderRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .analyticsTags(analyticsTags)
                        .payloadUpdate(payloadUpdate)
                        .moduleContext(moduleContext)
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
