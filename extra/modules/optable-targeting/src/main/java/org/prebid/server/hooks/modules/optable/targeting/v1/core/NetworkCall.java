package org.prebid.server.hooks.modules.optable.targeting.v1.core;

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
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.Objects;

public class NetworkCall {

    private final OptableTargeting optableTargeting;
    private final UserFpdActivityMask userFpdActivityMask;

    public NetworkCall(OptableTargeting optableTargeting, UserFpdActivityMask userFpdActivityMask) {

        this.optableTargeting = Objects.requireNonNull(optableTargeting);
        this.userFpdActivityMask = Objects.requireNonNull(userFpdActivityMask);
    }

    public Future<TargetingResult> makeRequest(AuctionRequestPayload payload,
                                         AuctionInvocationContext invocationContext,
                                         OptableTargetingProperties properties) {

        final BidRequest bidRequest = applyActivityRestrictions(payload.bidRequest(), invocationContext);

        final Timeout timeout = getHookTimeout(invocationContext);
        final OptableAttributes attributes = OptableAttributesResolver.resolveAttributes(
                invocationContext.auctionContext(),
                properties.getTimeout());

        return optableTargeting.getTargeting(properties, bidRequest, attributes, timeout);
    }

    private static Timeout getHookTimeout(AuctionInvocationContext invocationContext) {
        return invocationContext.timeout();
    }

    private BidRequest applyActivityRestrictions(BidRequest bidRequest,
                                                 AuctionInvocationContext auctionInvocationContext) {

        final AuctionContext auctionContext = auctionInvocationContext.auctionContext();
        final ActivityInvocationPayload activityInvocationPayload = BidRequestActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.GENERAL_MODULE, OptableTargetingModule.CODE),
                bidRequest);
        final ActivityInfrastructure activityInfrastructure = auctionContext.getActivityInfrastructure();

        final boolean disallowTransmitUfpd = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_UFPD, activityInvocationPayload);
        final boolean disallowTransmitEids = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_EIDS, activityInvocationPayload);
        final boolean disallowTransmitGeo = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_GEO, activityInvocationPayload);

        return maskUserPersonalInfo(bidRequest, disallowTransmitUfpd, disallowTransmitEids, disallowTransmitGeo);
    }

    private BidRequest maskUserPersonalInfo(BidRequest bidRequest,
                                            boolean disallowTransmitUfpd,
                                            boolean disallowTransmitEids,
                                            boolean disallowTransmitGeo) {

        final User maskedUser = userFpdActivityMask.maskUser(
                bidRequest.getUser(), disallowTransmitUfpd, disallowTransmitEids);
        final Device maskedDevice = userFpdActivityMask.maskDevice(
                bidRequest.getDevice(), disallowTransmitUfpd, disallowTransmitGeo);

        return bidRequest.toBuilder()
                .user(maskedUser)
                .device(maskedDevice)
                .build();
    }
}
