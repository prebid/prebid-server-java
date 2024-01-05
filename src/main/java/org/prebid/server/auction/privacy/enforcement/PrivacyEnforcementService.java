package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service provides masking for OpenRTB client sensitive information.
 */
public class PrivacyEnforcementService {

    private final CoppaEnforcement coppaEnforcement;
    private final CcpaEnforcement ccpaEnforcement;
    private final TcfEnforcement tcfEnforcement;
    private final ActivityEnforcement activityEnforcement;

    public PrivacyEnforcementService(CoppaEnforcement coppaEnforcement,
                                     CcpaEnforcement ccpaEnforcement,
                                     TcfEnforcement tcfEnforcement,
                                     ActivityEnforcement activityEnforcement) {

        this.coppaEnforcement = Objects.requireNonNull(coppaEnforcement);
        this.ccpaEnforcement = Objects.requireNonNull(ccpaEnforcement);
        this.tcfEnforcement = Objects.requireNonNull(tcfEnforcement);
        this.activityEnforcement = Objects.requireNonNull(activityEnforcement);
    }

    public Future<List<BidderPrivacyResult>> mask(AuctionContext auctionContext,
                                                  Map<String, User> bidderToUser,
                                                  BidderAliases aliases) {

        // For now, COPPA masking all values, so we can omit TCF masking.
        return coppaEnforcement.isApplicable(auctionContext)
                ? coppaEnforcement.enforce(auctionContext, bidderToUser)
                : ccpaEnforcement.enforce(auctionContext, bidderToUser, aliases)
                .flatMap(ccpaResult -> tcfEnforcement.enforce(
                                auctionContext,
                                bidderToUser,
                                biddersToApplyTcf(bidderToUser.keySet(), ccpaResult),
                                aliases)
                        .map(tcfResult -> ListUtils.union(ccpaResult, tcfResult)))
                .flatMap(bidderPrivacyResults -> activityEnforcement.enforce(bidderPrivacyResults, auctionContext));
    }

    private static Set<String> biddersToApplyTcf(Set<String> bidders, List<BidderPrivacyResult> ccpaResult) {
        final Set<String> biddersToApplyTcf = new HashSet<>(bidders);
        ccpaResult.stream()
                .map(BidderPrivacyResult::getRequestBidder)
                .forEach(biddersToApplyTcf::remove);

        return biddersToApplyTcf;
    }
}
