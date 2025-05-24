package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.Reason;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AuctionResponseValidator;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ExecutionTimeResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PayloadResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class OptableTargetingAuctionResponseHook implements AuctionResponseHook {

    private static final String CODE = "optable-targeting-auction-response-hook";
    private static final String ACTIVITY_ENRICH_REQUEST = "optable-enrich-request";
    private static final String ACTIVITY_ENRICH_RESPONSE = "optable-enrich-response";
    private static final String STATUS_EXECUTION_TIME = "execution-time";
    private static final String STATUS_REASON = "reason";

    private final PayloadResolver payloadResolver;
    private final ConfigResolver configResolver;
    private final ExecutionTimeResolver executionTimeResolver;
    private final ObjectMapper objectMapper;

    public OptableTargetingAuctionResponseHook(
            PayloadResolver payloadResolver,
            ConfigResolver configResolver,
            ExecutionTimeResolver executionTimeResolver,
            ObjectMapper objectMapper) {

        this.payloadResolver = Objects.requireNonNull(payloadResolver);
        this.configResolver = Objects.requireNonNull(configResolver);
        this.executionTimeResolver = Objects.requireNonNull(executionTimeResolver);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(AuctionResponsePayload auctionResponsePayload,
                                                                 AuctionInvocationContext invocationContext) {

        final OptableTargetingProperties properties = configResolver.resolve(invocationContext.accountConfig());
        final boolean adserverTargeting = properties.getAdserverTargeting();

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        moduleContext.setAdserverTargetingEnabled(adserverTargeting);
        moduleContext.setOptableTargetingExecutionTime(
                executionTimeResolver.extractOptableTargetingExecutionTime(invocationContext));

        if (adserverTargeting) {
            final EnrichmentStatus validationStatus = AuctionResponseValidator.checkEnrichmentPossibility(
                    auctionResponsePayload.bidResponse(), moduleContext.getTargeting());
            moduleContext.setEnrichResponseStatus(validationStatus);

            if (validationStatus.getStatus() == Status.SUCCESS) {
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
            PayloadUpdate<AuctionResponsePayload> payloadUpdate, ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionResponsePayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(payloadUpdate)
                        .moduleContext(moduleContext)
                        .analyticsTags(toAnalyticTags(moduleContext))
                        .build());
    }

    private Future<InvocationResult<AuctionResponsePayload>> success(ModuleContext moduleContext) {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionResponsePayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .analyticsTags(toAnalyticTags(moduleContext))
                        .build());
    }

    private Tags toAnalyticTags(ModuleContext moduleContext) {
        final String requestEnrichmentStatus = toEnrichmentStatusValue(moduleContext.getEnrichRequestStatus());
        final EnrichmentStatus responseEnrichmentStatus = moduleContext.getEnrichResponseStatus();
        final String responseEnrichmentStatusValue = toEnrichmentStatusValue(responseEnrichmentStatus);
        final String responseEnrichmentStatusReason = toEnrichmentStatusReason(moduleContext.getEnrichResponseStatus());

        final List<Activity> activities = new ArrayList<>();
        activities.add(ActivityImpl.of(ACTIVITY_ENRICH_REQUEST,
                requestEnrichmentStatus,
                toResults(STATUS_EXECUTION_TIME, String.valueOf(moduleContext.getOptableTargetingExecutionTime()))));

        if (moduleContext.isAdserverTargetingEnabled()) {
            activities.add(ActivityImpl.of(ACTIVITY_ENRICH_RESPONSE,
                    responseEnrichmentStatusValue,
                    toResults(STATUS_REASON, responseEnrichmentStatusReason)));
        }

        return TagsImpl.of(activities);
    }

    private String toEnrichmentStatusValue(EnrichmentStatus enrichRequestStatus) {
        return Optional.ofNullable(enrichRequestStatus)
                .map(EnrichmentStatus::getStatus)
                .map(Status::getValue)
                .orElse(null);
    }

    private String toEnrichmentStatusReason(EnrichmentStatus enrichmentStatus) {
        return Optional.ofNullable(enrichmentStatus)
                .map(EnrichmentStatus::getReason)
                .map(Reason::getValue)
                .orElse(null);
    }

    private List<Result> toResults(String result, String value) {
        final ObjectNode resultDetails = objectMapper.createObjectNode().put(result, value);
        return Collections.singletonList(ResultImpl.of(null, resultDetails, null));
    }

    @Override
    public String code() {
        return CODE;
    }
}
