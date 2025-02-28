package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import lombok.AllArgsConstructor;
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
import java.util.function.Function;

@AllArgsConstructor
public class OptableTargetingAuctionResponseHook implements AuctionResponseHook {

    private static final String CODE = "optable-targeting-auction-response-hook";

    private AnalyticTagsResolver analyticTagsResolver;

    private PayloadResolver payloadResolver;

    boolean adserverTargeting;

    private AuctionResponseValidator auctionResponseValidator;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(AuctionResponsePayload auctionResponsePayload,
                                                                 AuctionInvocationContext invocationContext) {

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        moduleContext.setAdserverTargetingEnabled(adserverTargeting);

        if (adserverTargeting) {
            final EnrichmentStatus validationStatus = auctionResponseValidator.checkEnrichmentPossibility(
                    auctionResponsePayload.bidResponse(), moduleContext.getTargeting());
            moduleContext.setEnrichResponseStatus(validationStatus);

            if (validationStatus.status() == Status.SUCCESS) {
                return enrichedPayloadFuture(moduleContext);
            }
        }

        return successFeature(moduleContext);
    }

    private Future<InvocationResult<AuctionResponsePayload>> enrichedPayloadFuture(ModuleContext moduleContext) {
        final List<Audience> targeting = moduleContext.getTargeting();

        return CollectionUtils.isNotEmpty(targeting)
                ? updateFeature(payload -> enrichPayload(payload, targeting), moduleContext)
                : successFeature(moduleContext);
    }

    private AuctionResponsePayload enrichPayload(AuctionResponsePayload payload, List<Audience> targeting) {
        return AuctionResponsePayloadImpl.of(payloadResolver.enrichBidResponse(payload.bidResponse(), targeting));
    }

    private Future<InvocationResult<AuctionResponsePayload>> updateFeature(
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

    private Future<InvocationResult<AuctionResponsePayload>> successFeature(ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionResponsePayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .analyticsTags(analyticTagsResolver.resolve(moduleContext))
                        .build());
    }
}
