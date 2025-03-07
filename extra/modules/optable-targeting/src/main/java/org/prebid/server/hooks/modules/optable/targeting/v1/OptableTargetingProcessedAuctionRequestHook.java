package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.Metrics;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableAttributesResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PayloadResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.Optional;
import java.util.function.Function;

@AllArgsConstructor
public class OptableTargetingProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "optable-targeting-processed-auction-request-hook";

    private static final long DEFAULT_API_CALL_TIMEOUT = 1000L;

    private final OptableTargetingProperties properties;

    private OptableTargeting optableTargeting;

    private PayloadResolver payloadResolver;

    private final OptableAttributesResolver optableAttributesResolver;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final ModuleContext moduleContext = createModuleContext();

        final BidRequest bidRequest = getBidRequest(auctionRequestPayload);
        if (bidRequest == null) {
            return failedFeature(moduleContext);
        }

        final long timeout = getHookRemainTime(invocationContext);
        final OptableAttributes attributes = optableAttributesResolver.resolveAttributes(
                invocationContext.auctionContext(),
                properties.getTimeout());

        final Future<TargetingResult> targetingResultFuture = optableTargeting.getTargeting(
                bidRequest,
                attributes,
                timeout);

        if (targetingResultFuture == null) {
            return failedFeature(this::sanitizePayload, moduleContext);
        }

        return targetingResultFuture.compose(targetingResult -> enrichedPayloadFuture(targetingResult, moduleContext))
                .recover(throwable -> failedFeature(this::sanitizePayload, moduleContext));
    }

    private ModuleContext createModuleContext() {
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setMetrics(Metrics.builder().moduleStartTime(System.currentTimeMillis())
                        .build());

        return moduleContext;
    }

    private BidRequest getBidRequest(AuctionRequestPayload auctionRequestPayload) {
        return Optional.ofNullable(auctionRequestPayload)
                .map(AuctionRequestPayload::bidRequest)
                .orElse(null);
    }

    private long getHookRemainTime(AuctionInvocationContext invocationContext) {
        return Optional.ofNullable(invocationContext)
                .map(AuctionInvocationContext::timeout)
                .map(Timeout::remaining)
                .orElse(DEFAULT_API_CALL_TIMEOUT);
    }

    private Future<InvocationResult<AuctionRequestPayload>> enrichedPayloadFuture(TargetingResult targetingResult,
                                                                                  ModuleContext moduleContext) {

        moduleContext.setMetrics(moduleContext.getMetrics().toBuilder()
                .moduleFinishTime(System.currentTimeMillis())
                .build());

        if (targetingResult != null) {
            moduleContext.setTargeting(targetingResult.getAudience());
            moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());

            return updateFeature(payload -> {
                final AuctionRequestPayload sanitizedPayload = sanitizePayload(payload);
                return enrichPayload(sanitizedPayload, targetingResult);
            }, moduleContext);
        } else {
            return failedFeature(this::sanitizePayload, moduleContext);
        }
    }

    private AuctionRequestPayload enrichPayload(AuctionRequestPayload payload, TargetingResult targetingResult) {
        return AuctionRequestPayloadImpl.of(payloadResolver.enrichBidRequest(payload.bidRequest(), targetingResult));
    }

    private AuctionRequestPayload sanitizePayload(AuctionRequestPayload payload) {
        return AuctionRequestPayloadImpl.of(payloadResolver.clearBidRequest(payload.bidRequest()));
    }

    private Future<InvocationResult<AuctionRequestPayload>> updateFeature(
            Function<AuctionRequestPayload, AuctionRequestPayload> func,
            ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(func::apply)
                        .moduleContext(moduleContext)
                        .build());
    }

    private Future<InvocationResult<AuctionRequestPayload>> failedFeature(ModuleContext moduleContext) {
        moduleContext.setEnrichRequestStatus(EnrichmentStatus.fail());
        return succeededFeature(moduleContext);
    }

    private Future<InvocationResult<AuctionRequestPayload>> failedFeature(
            Function<AuctionRequestPayload, AuctionRequestPayload> func,
            ModuleContext moduleContext) {

        moduleContext.setEnrichRequestStatus(EnrichmentStatus.fail());

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(func::apply)
                        .moduleContext(moduleContext)
                        .build());
    }

    private Future<InvocationResult<AuctionRequestPayload>> succeededFeature(ModuleContext moduleContext) {
        moduleContext.setMetrics(moduleContext.getMetrics().toBuilder()
                .moduleFinishTime(System.currentTimeMillis())
                .build());

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .build());
    }
}
