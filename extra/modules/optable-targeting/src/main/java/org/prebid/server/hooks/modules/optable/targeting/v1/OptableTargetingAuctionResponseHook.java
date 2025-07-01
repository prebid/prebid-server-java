package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AuctionResponseValidator;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidResponseEnricher;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.util.List;
import java.util.Objects;

public class OptableTargetingAuctionResponseHook implements AuctionResponseHook {

    private static final String CODE = "optable-targeting-auction-response-hook";

    private final ConfigResolver configResolver;
    private final ObjectMapper objectMapper;

    public OptableTargetingAuctionResponseHook(ConfigResolver configResolver, ObjectMapper objectMapper) {
        this.configResolver = Objects.requireNonNull(configResolver);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(AuctionResponsePayload auctionResponsePayload,
                                                                 AuctionInvocationContext invocationContext) {

        final OptableTargetingProperties properties = configResolver.resolve(invocationContext.accountConfig());
        final boolean adserverTargeting = properties.getAdserverTargeting();

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        moduleContext.setAdserverTargetingEnabled(adserverTargeting);

        if (!adserverTargeting) {
            return success(moduleContext);
        }

        final EnrichmentStatus validationStatus = AuctionResponseValidator.checkEnrichmentPossibility(
                auctionResponsePayload.bidResponse(), moduleContext.getTargeting());
        moduleContext.setEnrichResponseStatus(validationStatus);

        return validationStatus.getStatus() == Status.SUCCESS
                ? enrichedPayload(moduleContext)
                : success(moduleContext);
    }

    private Future<InvocationResult<AuctionResponsePayload>> enrichedPayload(ModuleContext moduleContext) {
        final List<Audience> targeting = moduleContext.getTargeting();

        return CollectionUtils.isNotEmpty(targeting)
                ? update(BidResponseEnricher.of(objectMapper, targeting), moduleContext)
                : success(moduleContext);
    }

    private Future<InvocationResult<AuctionResponsePayload>> update(
            PayloadUpdate<AuctionResponsePayload> payloadUpdate,
            ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionResponsePayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(payloadUpdate)
                        .moduleContext(moduleContext)
                        .analyticsTags(AnalyticTagsResolver.toEnrichResponseAnalyticTags(moduleContext))
                        .build());
    }

    private Future<InvocationResult<AuctionResponsePayload>> success(ModuleContext moduleContext) {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionResponsePayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .analyticsTags(AnalyticTagsResolver.toEnrichResponseAnalyticTags(moduleContext))
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
