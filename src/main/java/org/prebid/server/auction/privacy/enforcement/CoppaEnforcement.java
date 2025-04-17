package org.prebid.server.auction.privacy.enforcement;

import io.vertx.core.Future;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdCoppaMask;
import org.prebid.server.metric.Metrics;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CoppaEnforcement implements PrivacyEnforcement {

    private final UserFpdCoppaMask userFpdCoppaMask;
    private final Metrics metrics;

    public CoppaEnforcement(UserFpdCoppaMask userFpdCoppaMask, Metrics metrics) {
        this.userFpdCoppaMask = Objects.requireNonNull(userFpdCoppaMask);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public Future<List<BidderPrivacyResult>> enforce(AuctionContext auctionContext,
                                                     BidderAliases aliases,
                                                     List<BidderPrivacyResult> results) {

        if (!isApplicable(auctionContext)) {
            return Future.succeededFuture(results);
        }

        final Set<String> bidders = results.stream()
                .map(BidderPrivacyResult::getRequestBidder)
                .collect(Collectors.toSet());

        metrics.updatePrivacyCoppaMetric(auctionContext.getActivityInfrastructure(), bidders);
        return Future.succeededFuture(enforce(results));
    }

    private List<BidderPrivacyResult> enforce(List<BidderPrivacyResult> results) {
        return results.stream()
                .map(result -> BidderPrivacyResult.builder()
                        .requestBidder(result.getRequestBidder())
                        .user(userFpdCoppaMask.maskUser(result.getUser()))
                        .device(userFpdCoppaMask.maskDevice(result.getDevice()))
                        .build())
                .toList();
    }

    private static boolean isApplicable(AuctionContext auctionContext) {
        return auctionContext.getPrivacyContext().getPrivacy().getCoppa() == 1;
    }
}
