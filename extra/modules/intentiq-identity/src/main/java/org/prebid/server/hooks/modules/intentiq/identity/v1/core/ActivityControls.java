package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityInvocationPayload;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityModule;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.util.Objects;

/**
 * Applies the host's and account's activity controls to the module's calls to IntentIQ, as component
 * {@code general} named {@code intentiq-identity}
 * (see https://docs.prebid.org/prebid-server/features/pbs-activitycontrols.html).
 *
 * <ul>
 *     <li>{@code enrichUfpd} - the resolution call is made only when the module may add ids to {@code user.eids}.</li>
 *     <li>{@code reportAnalytics} - the impression report is sent only when allowed.</li>
 *     <li>{@code transmitUfpd}, {@code transmitEids}, {@code transmitPreciseGeo} - the data of both calls is
 *     read from the request after the core's {@link UserFpdActivityMask} removed what may not be sent.</li>
 * </ul>
 */
public class ActivityControls {

    private static final ActivityInvocationPayload COMPONENT =
            ActivityInvocationPayloadImpl.of(ComponentType.GENERAL_MODULE, IntentiqIdentityModule.CODE);

    private final UserFpdActivityMask userFpdActivityMask;

    public ActivityControls(UserFpdActivityMask userFpdActivityMask) {
        this.userFpdActivityMask = Objects.requireNonNull(userFpdActivityMask);
    }

    public boolean isAllowed(AuctionInvocationContext invocationContext, BidRequest bidRequest, Activity activity) {
        final ActivityInfrastructure activityInfrastructure = activityInfrastructure(invocationContext);
        if (activityInfrastructure == null) {
            return true;
        }

        final ActivityInvocationPayload payload = bidRequest != null
                ? BidRequestActivityInvocationPayload.of(COMPONENT, bidRequest)
                : COMPONENT;
        return activityInfrastructure.isAllowed(activity, payload);
    }

    /** The request with the user data, eids and precise geo the module may not transmit masked. */
    public BidRequest maskForTransmission(AuctionInvocationContext invocationContext, BidRequest bidRequest) {
        if (bidRequest == null) {
            return null;
        }

        final boolean disallowTransmitUfpd = !isAllowed(invocationContext, bidRequest, Activity.TRANSMIT_UFPD);
        final boolean disallowTransmitEids = !isAllowed(invocationContext, bidRequest, Activity.TRANSMIT_EIDS);
        final boolean disallowTransmitGeo = !isAllowed(invocationContext, bidRequest, Activity.TRANSMIT_GEO);
        if (!disallowTransmitUfpd && !disallowTransmitEids && !disallowTransmitGeo) {
            return bidRequest;
        }

        return bidRequest.toBuilder()
                .user(userFpdActivityMask.maskUser(bidRequest.getUser(), disallowTransmitUfpd, disallowTransmitEids))
                .device(userFpdActivityMask.maskDevice(
                        bidRequest.getDevice(), disallowTransmitUfpd, disallowTransmitGeo))
                .build();
    }

    // The core always sets it on an auction; it is absent only in a context built without one.
    private static ActivityInfrastructure activityInfrastructure(AuctionInvocationContext invocationContext) {
        final AuctionContext auctionContext = invocationContext.auctionContext();
        return auctionContext != null ? auctionContext.getActivityInfrastructure() : null;
    }
}
