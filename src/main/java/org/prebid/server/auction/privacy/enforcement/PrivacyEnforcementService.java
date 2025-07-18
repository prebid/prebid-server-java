package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service provides masking for OpenRTB client sensitive information.
 */
public class PrivacyEnforcementService {

    private final List<PrivacyEnforcement> enforcements;

    public PrivacyEnforcementService(final List<PrivacyEnforcement> enforcements) {
        this.enforcements = Objects.requireNonNull(enforcements);
    }

    public Future<List<BidderPrivacyResult>> mask(AuctionContext auctionContext,
                                                  Map<String, Pair<User, Device>> bidderToUserAndDevice,
                                                  BidderAliases aliases) {

        final List<BidderPrivacyResult> initialResults = bidderToUserAndDevice.entrySet().stream()
                .map(entry -> BidderPrivacyResult.builder()
                        .requestBidder(entry.getKey())
                        .user(entry.getValue().getLeft())
                        .device(entry.getValue().getRight())
                        .build())
                .toList();

        Future<List<BidderPrivacyResult>> composedResult = Future.succeededFuture(initialResults);

        for (PrivacyEnforcement enforcement : enforcements) {
            composedResult = composedResult.compose(
                    results -> enforcement.enforce(auctionContext, aliases, results));
        }

        return composedResult;
    }
}
