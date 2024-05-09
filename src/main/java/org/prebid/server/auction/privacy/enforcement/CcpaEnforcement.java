package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class CcpaEnforcement {

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
                                                     Map<String, User> bidderToUser,
                                                     BidderAliases aliases) {

        final Ccpa ccpa = auctionContext.getPrivacyContext().getPrivacy().getCcpa();
        metrics.updatePrivacyCcpaMetrics(ccpa.isNotEmpty(), ccpa.isEnforced());

        return Future.succeededFuture(enforce(bidderToUser, ccpa, auctionContext, aliases));
    }

    private List<BidderPrivacyResult> enforce(Map<String, User> bidderToUser,
                                              Ccpa ccpa,
                                              AuctionContext auctionContext,
                                              BidderAliases aliases) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Device device = bidRequest.getDevice();

        return isCcpaEnforced(ccpa, auctionContext.getAccount(), auctionContext.getRequestTypeMetric())
                ? maskCcpa(bidderToUser, extractCcpaEnforcedBidders(bidderToUser.keySet(), bidRequest, aliases), device)
                : Collections.emptyList();
    }

    public boolean isCcpaEnforced(Ccpa ccpa, Account account) {
        return isCcpaEnforced(ccpa, account, null);
    }

    private boolean isCcpaEnforced(Ccpa ccpa, Account account, MetricName requestType) {
        return ccpa.isEnforced() && isCcpaEnabled(account, requestType);
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

    private Set<String> extractCcpaEnforcedBidders(Set<String> bidders, BidRequest bidRequest, BidderAliases aliases) {
        final Set<String> ccpaEnforcedBidders = new HashSet<>(bidders);
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

    private List<BidderPrivacyResult> maskCcpa(Map<String, User> bidderToUser, Set<String> bidders, Device device) {
        final Device maskedDevice = userFpdCcpaMask.maskDevice(device);
        return bidders.stream()
                .map(bidder -> BidderPrivacyResult.builder()
                        .requestBidder(bidder)
                        .user(userFpdCcpaMask.maskUser(bidderToUser.get(bidder)))
                        .device(maskedDevice)
                        .build())
                .toList();
    }
}
