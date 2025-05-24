package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityInvocationPayload;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
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
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class OptableTargetingProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    public static final String CODE = "optable-targeting-processed-auction-request-hook";
    private static final long DEFAULT_API_CALL_TIMEOUT = 1000L;
    private final ConfigResolver configResolver;
    private final OptableTargeting optableTargeting;
    private final PayloadResolver payloadResolver;
    private final OptableAttributesResolver optableAttributesResolver;
    private final UserFpdActivityMask userFpdActivityMask;

    public OptableTargetingProcessedAuctionRequestHook(ConfigResolver configResolver,
                                                       OptableTargeting optableTargeting,
                                                       PayloadResolver payloadResolver,
                                                       OptableAttributesResolver optableAttributesResolver,
                                                       UserFpdActivityMask userFpdActivityMask) {

        this.configResolver = Objects.requireNonNull(configResolver);
        this.optableTargeting = Objects.requireNonNull(optableTargeting);
        this.payloadResolver = Objects.requireNonNull(payloadResolver);
        this.optableAttributesResolver = Objects.requireNonNull(optableAttributesResolver);
        this.userFpdActivityMask = Objects.requireNonNull(userFpdActivityMask);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final OptableTargetingProperties properties = configResolver.resolve(invocationContext.accountConfig());
        final ModuleContext moduleContext = new ModuleContext();

        final BidRequest bidRequest = applyActivityRestrictions(auctionRequestPayload.bidRequest(), invocationContext);

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
            moduleContext.setEnrichRequestStatus(EnrichmentStatus.failure());
            return failure(this::sanitizePayload, moduleContext);
        }

        return targetingResultFuture.compose(targetingResult -> enrichedPayload(targetingResult, moduleContext))
                .recover(throwable -> {
                    moduleContext.setEnrichRequestStatus(EnrichmentStatus.failure());
                    return failure(this::sanitizePayload, moduleContext);
                });
    }

    private BidRequest applyActivityRestrictions(BidRequest bidRequest,
                                                 AuctionInvocationContext auctionInvocationContext) {
        if (bidRequest == null) {
            return null;
        }

        final AuctionContext auctionContext = auctionInvocationContext.auctionContext();
        final ActivityInvocationPayload activityInvocationPayload = BidRequestActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.GENERAL_MODULE, OptableTargetingModule.CODE),
                bidRequest);
        final ActivityInfrastructure activityInfrastructure = auctionContext.getActivityInfrastructure();

        final boolean disallowTransmitUfpd = !activityInfrastructure.isAllowed(Activity.TRANSMIT_UFPD,
                activityInvocationPayload);
        final boolean disallowTransmitEids = !activityInfrastructure.isAllowed(Activity.TRANSMIT_EIDS,
                activityInvocationPayload);
        final boolean disallowTransmitGeo = !activityInfrastructure.isAllowed(Activity.TRANSMIT_GEO,
                activityInvocationPayload);

        return maskUserPersonalInfo(bidRequest, disallowTransmitUfpd, disallowTransmitEids, disallowTransmitGeo);
    }

    private BidRequest maskUserPersonalInfo(BidRequest bidRequest, boolean disallowTransmitUfpd,
                                            boolean disallowTransmitEids, boolean disallowTransmitGeo) {

        final User maskedUser = userFpdActivityMask.maskUser(
                bidRequest.getUser(), disallowTransmitUfpd, disallowTransmitEids);
        final Device maskedDevice = userFpdActivityMask.maskDevice(
                bidRequest.getDevice(), disallowTransmitUfpd, disallowTransmitGeo);

        return bidRequest.toBuilder()
                .user(maskedUser)
                .device(maskedDevice)
                .build();
    }

    private long getHookRemainingTime(AuctionInvocationContext invocationContext) {
        return Optional.ofNullable(invocationContext)
                .map(AuctionInvocationContext::timeout)
                .map(Timeout::remaining)
                .orElse(DEFAULT_API_CALL_TIMEOUT);
    }

    private Future<InvocationResult<AuctionRequestPayload>> enrichedPayload(TargetingResult targetingResult,
                                                                            ModuleContext moduleContext) {

        if (targetingResult != null) {
            moduleContext.setTargeting(targetingResult.getAudience());
            moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());

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
            PayloadUpdate<AuctionRequestPayload> func,
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
        moduleContext.setEnrichRequestStatus(EnrichmentStatus.failure());
        return success(moduleContext);
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
