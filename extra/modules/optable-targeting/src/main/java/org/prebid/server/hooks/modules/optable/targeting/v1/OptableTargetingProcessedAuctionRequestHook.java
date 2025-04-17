package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.Metrics;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableAttributesResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PayloadResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class OptableTargetingProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "optable-targeting-processed-auction-request-hook";

    private static final long DEFAULT_API_CALL_TIMEOUT = 1000L;

    private final ConfigResolver configResolver;

    private final OptableTargeting optableTargeting;

    private final PayloadResolver payloadResolver;

    private final OptableAttributesResolver optableAttributesResolver;

    public OptableTargetingProcessedAuctionRequestHook(ConfigResolver configResolver,
                                                       OptableTargeting optableTargeting,
                                                       PayloadResolver payloadResolver,
                                                       OptableAttributesResolver optableAttributesResolver) {

        this.configResolver = Objects.requireNonNull(configResolver);
        this.optableTargeting = Objects.requireNonNull(optableTargeting);
        this.payloadResolver = Objects.requireNonNull(payloadResolver);
        this.optableAttributesResolver = Objects.requireNonNull(optableAttributesResolver);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final OptableTargetingProperties properties = configResolver.resolve(invocationContext.accountConfig());
        final ModuleContext moduleContext = new ModuleContext()
                .setMetrics(Metrics.builder()
                .moduleStartTime(System.currentTimeMillis())
                .build());

        final BidRequest bidRequest = getBidRequest(auctionRequestPayload);
        if (bidRequest == null) {
            return failure(moduleContext);
        }

        final long timeout = getHookRemainingTime(invocationContext);
        final OptableAttributes attributes = optableAttributesResolver.resolveAttributes(
                invocationContext.auctionContext(),
                properties.getTimeout());

        final Future<TargetingResult> targetingResultFuture = optableTargeting.getTargeting(
                properties,
                bidRequest,
                attributes,
                timeout);

        if (targetingResultFuture == null) {
            return failure(
                    this::sanitizePayload,
                    moduleContext.setEnrichRequestStatus(EnrichmentStatus.failure()));
        }

        return targetingResultFuture.compose(targetingResult -> enrichedPayload(targetingResult, moduleContext))
                .recover(throwable -> failure(
                        this::sanitizePayload,
                        moduleContext.setEnrichRequestStatus(EnrichmentStatus.failure())));
    }

    private BidRequest getBidRequest(AuctionRequestPayload auctionRequestPayload) {
        return Optional.ofNullable(auctionRequestPayload)
                .map(AuctionRequestPayload::bidRequest)
                .orElse(null);
    }

    private long getHookRemainingTime(AuctionInvocationContext invocationContext) {
        return Optional.ofNullable(invocationContext)
                .map(AuctionInvocationContext::timeout)
                .map(Timeout::remaining)
                .orElse(DEFAULT_API_CALL_TIMEOUT);
    }

    private Future<InvocationResult<AuctionRequestPayload>> enrichedPayload(TargetingResult targetingResult,
                                                                            ModuleContext moduleContext) {

        moduleContext.setFinishTime(System.currentTimeMillis());

        if (targetingResult != null) {
            moduleContext.setTargeting(targetingResult.getAudience())
                    .setEnrichRequestStatus(EnrichmentStatus.success());

            return update(payload -> {
                final AuctionRequestPayload sanitizedPayload = sanitizePayload(payload);
                return enrichPayload(sanitizedPayload, targetingResult);
            }, moduleContext);
        } else {
            moduleContext.setEnrichRequestStatus(EnrichmentStatus.failure());
            return failure(this::sanitizePayload, moduleContext);
        }
    }

    private AuctionRequestPayload enrichPayload(AuctionRequestPayload payload, TargetingResult targetingResult) {
        return AuctionRequestPayloadImpl.of(payloadResolver.enrichBidRequest(payload.bidRequest(), targetingResult));
    }

    private AuctionRequestPayload sanitizePayload(AuctionRequestPayload payload) {
        return AuctionRequestPayloadImpl.of(payloadResolver.clearBidRequest(payload.bidRequest()));
    }

    private static Future<InvocationResult<AuctionRequestPayload>> update(
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

    private static Future<InvocationResult<AuctionRequestPayload>> failure(ModuleContext moduleContext) {
        return success(moduleContext
                .setEnrichRequestStatus(EnrichmentStatus.failure())
                .setFinishTime(System.currentTimeMillis()));
    }

    private static Future<InvocationResult<AuctionRequestPayload>> failure(
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

    private static Future<InvocationResult<AuctionRequestPayload>> success(ModuleContext moduleContext) {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
