package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ActivityEnforcement {

    private final UserFpdActivityMask userFpdActivityMask;

    public ActivityEnforcement(UserFpdActivityMask userFpdActivityMask) {
        this.userFpdActivityMask = Objects.requireNonNull(userFpdActivityMask);
    }

    public Future<List<BidderPrivacyResult>> enforce(List<BidderPrivacyResult> bidderPrivacyResults,
                                                     AuctionContext auctionContext) {

        final List<BidderPrivacyResult> results = bidderPrivacyResults.stream()
                .map(bidderPrivacyResult -> applyActivityRestrictions(
                        bidderPrivacyResult,
                        auctionContext.getActivityInfrastructure(),
                        auctionContext.getBidRequest()))
                .toList();

        return Future.succeededFuture(results);
    }

    private BidderPrivacyResult applyActivityRestrictions(BidderPrivacyResult bidderPrivacyResult,
                                                          ActivityInfrastructure infrastructure,
                                                          BidRequest bidRequest) {

        final String bidder = bidderPrivacyResult.getRequestBidder();
        final User user = bidderPrivacyResult.getUser();
        final Device device = bidderPrivacyResult.getDevice();

        final ActivityInvocationPayload payload = activityInvocationPayload(
                bidder, device != null ? device.getGeo() : null, bidRequest);

        final boolean disallowTransmitUfpd = !infrastructure.isAllowed(Activity.TRANSMIT_UFPD, payload);
        final boolean disallowTransmitEids = !infrastructure.isAllowed(Activity.TRANSMIT_EIDS, payload);
        final boolean disallowTransmitGeo = !infrastructure.isAllowed(Activity.TRANSMIT_GEO, payload);

        final User resolvedUser = userFpdActivityMask.maskUser(user, disallowTransmitUfpd, disallowTransmitEids);
        final Device resolvedDevice = userFpdActivityMask.maskDevice(device, disallowTransmitUfpd, disallowTransmitGeo);

        return bidderPrivacyResult.toBuilder()
                .user(resolvedUser)
                .device(resolvedDevice)
                .build();
    }

    private static ActivityInvocationPayload activityInvocationPayload(String bidder,
                                                                       Geo geo,
                                                                       BidRequest bidRequest) {

        return ActivityInvocationPayload.builder()
                .component(ComponentType.BIDDER, bidder)
                .geo(geo != null ? geo.getCountry() : null, geo != null ? geo.getRegion() : null)
                .gpc(Optional.ofNullable(bidRequest.getRegs())
                        .map(Regs::getExt)
                        .map(ExtRegs::getGpc)
                        .orElse(null))
                .build();
    }
}
