package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdCoppaMask;
import org.prebid.server.metric.Metrics;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CoppaEnforcement {

    private final UserFpdCoppaMask userFpdCoppaMask;
    private final Metrics metrics;

    public CoppaEnforcement(UserFpdCoppaMask userFpdCoppaMask, Metrics metrics) {
        this.userFpdCoppaMask = Objects.requireNonNull(userFpdCoppaMask);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public boolean isApplicable(AuctionContext auctionContext) {
        return auctionContext.getPrivacyContext().getPrivacy().getCoppa() == 1;
    }

    public Future<List<BidderPrivacyResult>> enforce(AuctionContext auctionContext, Map<String, User> bidderToUser) {
        metrics.updatePrivacyCoppaMetric(auctionContext.getActivityInfrastructure(), bidderToUser.keySet());
        return Future.succeededFuture(results(bidderToUser, auctionContext.getBidRequest().getDevice()));
    }

    private List<BidderPrivacyResult> results(Map<String, User> bidderToUser, Device device) {
        final Device maskedDevice = userFpdCoppaMask.maskDevice(device);
        return bidderToUser.entrySet().stream()
                .map(bidderAndUser -> BidderPrivacyResult.builder()
                        .requestBidder(bidderAndUser.getKey())
                        .user(userFpdCoppaMask.maskUser(bidderAndUser.getValue()))
                        .device(maskedDevice)
                        .build())
                .toList();
    }
}
