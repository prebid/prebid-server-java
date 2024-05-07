package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdTcfMask;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.VendorIdResolver;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TcfEnforcement {

    private static final Logger logger = LoggerFactory.getLogger(TcfEnforcement.class);

    private final TcfDefinerService tcfDefinerService;
    private final UserFpdTcfMask userFpdTcfMask;
    private final BidderCatalog bidderCatalog;
    private final Metrics metrics;
    private final boolean lmtEnforce;

    public TcfEnforcement(TcfDefinerService tcfDefinerService,
                          UserFpdTcfMask userFpdTcfMask,
                          BidderCatalog bidderCatalog,
                          Metrics metrics,
                          boolean lmtEnforce) {

        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.userFpdTcfMask = Objects.requireNonNull(userFpdTcfMask);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.metrics = Objects.requireNonNull(metrics);
        this.lmtEnforce = lmtEnforce;
    }

    public Future<Map<Integer, PrivacyEnforcementAction>> enforce(Set<Integer> vendorIds, TcfContext tcfContext) {
        return tcfDefinerService.resultForVendorIds(vendorIds, tcfContext)
                .map(TcfResponse::getActions);
    }

    public Future<List<BidderPrivacyResult>> enforce(AuctionContext auctionContext,
                                                     Map<String, User> bidderToUser,
                                                     Set<String> bidders,
                                                     BidderAliases aliases) {

        final Device device = auctionContext.getBidRequest().getDevice();
        final AccountGdprConfig accountGdprConfig = accountGdprConfig(auctionContext.getAccount());
        final MetricName requestType = auctionContext.getRequestTypeMetric();

        return tcfDefinerService.resultForBidderNames(
                        bidders,
                        VendorIdResolver.of(aliases, bidderCatalog),
                        auctionContext.getPrivacyContext().getTcfContext(),
                        accountGdprConfig)
                .map(TcfResponse::getActions)
                .map(enforcements -> updateMetrics(enforcements, aliases, requestType, bidderToUser, device))
                .map(enforcements -> bidderToPrivacyResult(enforcements, bidders, bidderToUser, device));
    }

    private static AccountGdprConfig accountGdprConfig(Account account) {
        final AccountPrivacyConfig privacyConfig = account.getPrivacy();
        return privacyConfig != null ? privacyConfig.getGdpr() : null;
    }

    private Map<String, PrivacyEnforcementAction> updateMetrics(Map<String, PrivacyEnforcementAction> enforcements,
                                                                BidderAliases aliases,
                                                                MetricName requestType,
                                                                Map<String, User> bidderToUser,
                                                                Device device) {

        // Metrics should represent real picture of the bidding process, so if bidder request is blocked
        // by privacy then no reason to increment another metrics, like geo masked, etc.
        for (final Map.Entry<String, PrivacyEnforcementAction> bidderEnforcement : enforcements.entrySet()) {
            final String bidder = bidderEnforcement.getKey();
            final PrivacyEnforcementAction enforcement = bidderEnforcement.getValue();
            final User user = bidderToUser.get(bidder);

            final boolean requestBlocked = enforcement.isBlockBidderRequest();
            final boolean ufpdRemoved = !requestBlocked
                    && ((enforcement.isRemoveUserFpd() && shouldRemoveUserData(user))
                    || (enforcement.isMaskDeviceInfo() && shouldRemoveDeviceData(device)));
            final boolean uidsRemoved = !requestBlocked && enforcement.isRemoveUserIds() && shouldRemoveUids(user);
            final boolean geoMasked = !requestBlocked && enforcement.isMaskGeo() && shouldMaskGeo(user, device);
            final boolean analyticsBlocked = !requestBlocked && enforcement.isBlockAnalyticsReport();

            metrics.updateAuctionTcfMetrics(
                    aliases.resolveBidder(bidder),
                    requestType,
                    ufpdRemoved,
                    uidsRemoved,
                    geoMasked,
                    analyticsBlocked,
                    requestBlocked);

            if (ufpdRemoved) {
                logger.warn("The UFPD fields have been removed due to a consent check.");
            }
        }

        if (isLmtEnforcedAndEnabled(device)) {
            metrics.updatePrivacyLmtMetric();
        }

        return enforcements;
    }

    private static boolean shouldRemoveUserData(User user) {
        return user != null && ObjectUtils.anyNotNull(
                user.getId(),
                user.getBuyeruid(),
                user.getYob(),
                user.getGender(),
                user.getKeywords(),
                user.getKwarray(),
                user.getData(),
                ObjectUtil.getIfNotNull(user.getExt(), ExtUser::getData));
    }

    private static boolean shouldRemoveDeviceData(Device device) {
        return device != null && ObjectUtils.anyNotNull(
                device.getIfa(),
                device.getMacsha1(), device.getMacmd5(),
                device.getDpidsha1(), device.getDpidmd5(),
                device.getDidsha1(), device.getDidmd5());
    }

    private static boolean shouldRemoveUids(User user) {
        return user != null && CollectionUtils.isNotEmpty(user.getEids());
    }

    private static boolean shouldMaskGeo(User user, Device device) {
        return (user != null && user.getGeo() != null) || (device != null && device.getGeo() != null);
    }

    private boolean isLmtEnforcedAndEnabled(Device device) {
        return lmtEnforce && device != null && Objects.equals(device.getLmt(), 1);
    }

    private List<BidderPrivacyResult> bidderToPrivacyResult(Map<String, PrivacyEnforcementAction> bidderToEnforcement,
                                                            Set<String> bidders,
                                                            Map<String, User> bidderToUser,
                                                            Device device) {

        final boolean isLmtEnabled = isLmtEnforcedAndEnabled(device);

        return bidders.stream()
                .map(bidder -> createBidderPrivacyResult(
                        bidder,
                        bidderToUser.get(bidder),
                        device,
                        bidderToEnforcement,
                        isLmtEnabled))
                .toList();
    }

    private BidderPrivacyResult createBidderPrivacyResult(String bidder,
                                                          User user,
                                                          Device device,
                                                          Map<String, PrivacyEnforcementAction> bidderToEnforcement,
                                                          boolean isLmtEnabled) {

        final PrivacyEnforcementAction privacyEnforcementAction = bidderToEnforcement.get(bidder);
        final boolean blockBidderRequest = privacyEnforcementAction.isBlockBidderRequest();
        final boolean blockAnalyticsReport = privacyEnforcementAction.isBlockAnalyticsReport();
        if (blockBidderRequest) {
            return BidderPrivacyResult.builder()
                    .requestBidder(bidder)
                    .blockedRequestByTcf(true)
                    .blockedAnalyticsByTcf(blockAnalyticsReport)
                    .build();
        }

        final boolean maskUserFpd = privacyEnforcementAction.isRemoveUserFpd() || isLmtEnabled;
        final boolean maskUserIds = privacyEnforcementAction.isRemoveUserIds() || isLmtEnabled;
        final boolean maskGeo = privacyEnforcementAction.isMaskGeo() || isLmtEnabled;
        final Set<String> eidExceptions = privacyEnforcementAction.getEidExceptions();
        final User maskedUser = userFpdTcfMask.maskUser(user, maskUserFpd, maskUserIds, maskGeo, eidExceptions);

        final boolean maskIp = privacyEnforcementAction.isMaskDeviceIp() || isLmtEnabled;
        final boolean maskDeviceInfo = privacyEnforcementAction.isMaskDeviceInfo() || isLmtEnabled;
        final Device maskedDevice = userFpdTcfMask.maskDevice(device, maskIp, maskGeo, maskDeviceInfo);

        return BidderPrivacyResult.builder()
                .requestBidder(bidder)
                .user(maskedUser)
                .device(maskedDevice)
                .blockedAnalyticsByTcf(blockAnalyticsReport)
                .build();
    }
}
