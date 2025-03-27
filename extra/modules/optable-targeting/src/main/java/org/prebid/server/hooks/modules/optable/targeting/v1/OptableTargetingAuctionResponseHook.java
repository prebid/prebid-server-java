package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.v1.analytics.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AuctionResponseValidator;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PayloadResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class OptableTargetingAuctionResponseHook implements AuctionResponseHook {

    private static final String CODE = "optable-targeting-auction-response-hook";

    private final AnalyticTagsResolver analyticTagsResolver;

    private final PayloadResolver payloadResolver;

    private final boolean adserverTargeting;

    public OptableTargetingAuctionResponseHook(
            AnalyticTagsResolver analyticTagsResolver,
            PayloadResolver payloadResolver,
            boolean adserverTargeting) {

        this.analyticTagsResolver = Objects.requireNonNull(analyticTagsResolver);
        this.payloadResolver = Objects.requireNonNull(payloadResolver);
        this.adserverTargeting = adserverTargeting;
    }

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(AuctionResponsePayload auctionResponsePayload,
                                                                 AuctionInvocationContext invocationContext) {

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        moduleContext.setAdserverTargetingEnabled(adserverTargeting);

        if (adserverTargeting) {
            final EnrichmentStatus validationStatus = AuctionResponseValidator.checkEnrichmentPossibility(
                    auctionResponsePayload.bidResponse(), moduleContext.getTargeting());
            moduleContext.setEnrichResponseStatus(validationStatus);

            if (validationStatus.status() == Status.SUCCESS) {
                return enrichedPayload(moduleContext);
            }
        }

        return success(moduleContext);
    }

    private Future<InvocationResult<AuctionResponsePayload>> enrichedPayload(ModuleContext moduleContext) {
        final List<Audience> targeting = moduleContext.getTargeting();

        return CollectionUtils.isNotEmpty(targeting)
                ? update(payload -> enrichPayload(payload, targeting), moduleContext)
                : success(moduleContext);
    }

    private AuctionResponsePayload enrichPayload(AuctionResponsePayload payload, List<Audience> targeting) {
        return AuctionResponsePayloadImpl.of(payloadResolver.enrichBidResponse(payload.bidResponse(), targeting));
    }

    private Future<InvocationResult<AuctionResponsePayload>> update(
            Function<AuctionResponsePayload, AuctionResponsePayload> func, ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionResponsePayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(func::apply)
                        .moduleContext(moduleContext)
                        .analyticsTags(analyticTagsResolver.resolve(moduleContext))
                        .build());
    }

    private Future<InvocationResult<AuctionResponsePayload>> success(ModuleContext moduleContext) {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionResponsePayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .analyticsTags(analyticTagsResolver.resolve(moduleContext))
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
