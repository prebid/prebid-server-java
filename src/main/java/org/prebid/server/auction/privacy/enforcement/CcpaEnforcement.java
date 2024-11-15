package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdCcpaMask;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCcpaConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CcpaEnforcement implements PrivacyEnforcement {

    private static final String CATCH_ALL_BIDDERS = "*";

    private final UserFpdCcpaMask userFpdCcpaMask;
    private final BidderCatalog bidderCatalog;
    private final Metrics metrics;
    private final boolean ccpaEnforce;

    public CcpaEnforcement(UserFpdCcpaMask userFpdCcpaMask,
                           BidderCatalog bidderCatalog,
                           Metrics metrics,
                           boolean ccpaEnforce) {

        this.userFpdCcpaMask = Objects.requireNonNull(userFpdCcpaMask);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.metrics = Objects.requireNonNull(metrics);
        this.ccpaEnforce = ccpaEnforce;
    }

    public Future<List<BidderPrivacyResult>> enforce(AuctionContext auctionContext,
                                                     BidderAliases aliases,
                                                     List<BidderPrivacyResult> results) {

        final Ccpa ccpa = auctionContext.getPrivacyContext().getPrivacy().getCcpa();
        final BidRequest bidRequest = auctionContext.getBidRequest();

        final boolean isCcpaEnforced = ccpa.isEnforced();
        final boolean isCcpaEnabled = isCcpaEnabled(auctionContext.getAccount(), auctionContext.getRequestTypeMetric());

        final Set<String> enforcedBidders = isCcpaEnabled && isCcpaEnforced
                ? extractCcpaEnforcedBidders(results, bidRequest, aliases)
                : Collections.emptySet();

        metrics.updatePrivacyCcpaMetrics(
                auctionContext.getActivityInfrastructure(),
                ccpa.isNotEmpty(),
                isCcpaEnforced,
                isCcpaEnabled,
                enforcedBidders);

        final List<BidderPrivacyResult> enforcedResults = results.stream()
                .map(result -> enforcedBidders.contains(result.getRequestBidder()) ? maskCcpa(result) : result)
                .toList();

        return Future.succeededFuture(enforcedResults);
    }

    public boolean isCcpaEnforced(Ccpa ccpa, Account account) {
        return ccpa.isEnforced() && isCcpaEnabled(account, null);
    }

    private boolean isCcpaEnabled(Account account, MetricName requestType) {
        final Optional<AccountCcpaConfig> accountCcpaConfig = Optional.ofNullable(account.getPrivacy())
                .map(AccountPrivacyConfig::getCcpa);

        return ObjectUtils.firstNonNull(
                accountCcpaConfig
                        .map(AccountCcpaConfig::getEnabledForRequestType)
                        .map(enabledForRequestType -> enabledForRequestType.isEnabledFor(requestType))
                        .orElse(null),
                accountCcpaConfig
                        .map(AccountCcpaConfig::getEnabled)
                        .orElse(null),
                ccpaEnforce);
    }

    private Set<String> extractCcpaEnforcedBidders(List<BidderPrivacyResult> results,
                                                   BidRequest bidRequest,
                                                   BidderAliases aliases) {

        final Set<String> ccpaEnforcedBidders = results.stream()
                .map(BidderPrivacyResult::getRequestBidder)
                .collect(Collectors.toCollection(HashSet::new));

        final List<String> nosaleBidders = Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getNosale)
                .orElseGet(Collections::emptyList);

        if (nosaleBidders.size() == 1 && nosaleBidders.contains(CATCH_ALL_BIDDERS)) {
            ccpaEnforcedBidders.clear();
        } else {
            nosaleBidders.forEach(ccpaEnforcedBidders::remove);
        }

        ccpaEnforcedBidders.removeIf(bidder ->
                !bidderCatalog.bidderInfoByName(aliases.resolveBidder(bidder)).isCcpaEnforced());

        return ccpaEnforcedBidders;
    }

    private BidderPrivacyResult maskCcpa(BidderPrivacyResult result) {
        return BidderPrivacyResult.builder()
                .requestBidder(result.getRequestBidder())
                .user(userFpdCcpaMask.maskUser(result.getUser()))
                .device(userFpdCcpaMask.maskDevice(result.getDevice()))
                .build();
    }
}
