package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
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
import java.util.stream.Collectors;

public class TcfEnforcement implements PrivacyEnforcement {

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

    @Override
    public Future<List<BidderPrivacyResult>> enforce(AuctionContext auctionContext,
                                                     BidderAliases aliases,
                                                     List<BidderPrivacyResult> results) {

        final MetricName requestType = auctionContext.getRequestTypeMetric();
        final ActivityInfrastructure activityInfrastructure = auctionContext.getActivityInfrastructure();
        final Set<String> bidders = results.stream()
                .map(BidderPrivacyResult::getRequestBidder)
                .collect(Collectors.toSet());

        return tcfDefinerService.resultForBidderNames(
                        bidders,
                        VendorIdResolver.of(aliases, bidderCatalog),
                        auctionContext.getPrivacyContext().getTcfContext(),
                        accountGdprConfig(auctionContext.getAccount()))
                .map(TcfResponse::getActions)
                .map(enforcements -> updateMetrics(activityInfrastructure, enforcements, aliases, requestType, results))
                .map(enforcements -> applyEnforcements(enforcements, results));
    }

    private static AccountGdprConfig accountGdprConfig(Account account) {
        final AccountPrivacyConfig privacyConfig = account.getPrivacy();
        return privacyConfig != null ? privacyConfig.getGdpr() : null;
    }

    private Map<String, PrivacyEnforcementAction> updateMetrics(ActivityInfrastructure activityInfrastructure,
                                                                Map<String, PrivacyEnforcementAction> enforcements,
                                                                BidderAliases aliases,
                                                                MetricName requestType,
                                                                List<BidderPrivacyResult> results) {


        // Metrics should represent real picture of the bidding process, so if bidder request is blocked
        // by privacy then no reason to increment another metrics, like geo masked, etc.
        for (BidderPrivacyResult result : results) {
            final String bidder = result.getRequestBidder();
            final User user = result.getUser();
            final Device device = result.getDevice();
            final PrivacyEnforcementAction enforcement = enforcements.get(bidder);

            final boolean requestBlocked = enforcement.isBlockBidderRequest();
            final boolean ufpdRemoved = !requestBlocked
                    && ((enforcement.isRemoveUserFpd() && shouldRemoveUserData(user))
                    || (enforcement.isMaskDeviceInfo() && shouldRemoveDeviceData(device)));
            final boolean isLmtEnforcedAndEnabled = isLmtEnforcedAndEnabled(device);
            final boolean uidsRemoved = !requestBlocked && enforcement.isRemoveUserIds() && shouldRemoveUids(user);
            final boolean geoMasked = !requestBlocked && enforcement.isMaskGeo() && shouldMaskGeo(user, device);
            final boolean analyticsBlocked = !requestBlocked && enforcement.isBlockAnalyticsReport();

            metrics.updateAuctionTcfAndLmtMetrics(
                    activityInfrastructure,
                    aliases.resolveBidder(bidder),
                    requestType,
                    ufpdRemoved,
                    uidsRemoved,
                    geoMasked,
                    analyticsBlocked,
                    requestBlocked,
                    isLmtEnforcedAndEnabled);

            if (ufpdRemoved) {
                logger.warn("The UFPD fields have been removed due to a consent check.");
            }
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

    private List<BidderPrivacyResult> applyEnforcements(Map<String, PrivacyEnforcementAction> enforcements,
                                                        List<BidderPrivacyResult> results) {

        return results.stream()
                .map(result -> applyEnforcement(enforcements.get(result.getRequestBidder()), result))
                .toList();
    }

    private BidderPrivacyResult applyEnforcement(PrivacyEnforcementAction enforcement, BidderPrivacyResult result) {
        final String bidder = result.getRequestBidder();

        final boolean blockBidderRequest = enforcement.isBlockBidderRequest();
        final boolean blockAnalyticsReport = enforcement.isBlockAnalyticsReport();

        if (blockBidderRequest) {
            return BidderPrivacyResult.builder()
                    .requestBidder(bidder)
                    .blockedRequestByTcf(true)
                    .blockedAnalyticsByTcf(blockAnalyticsReport)
                    .build();
        }

        final User user = result.getUser();
        final Device device = result.getDevice();

        final boolean isLmtEnabled = isLmtEnforcedAndEnabled(device);
        final boolean maskUserFpd = enforcement.isRemoveUserFpd() || isLmtEnabled;
        final boolean maskUserIds = enforcement.isRemoveUserIds() || isLmtEnabled;
        final boolean maskGeo = enforcement.isMaskGeo() || isLmtEnabled;
        final Set<String> eidExceptions = enforcement.getEidExceptions();
        final User maskedUser = userFpdTcfMask.maskUser(user, maskUserFpd, maskUserIds, eidExceptions);

        final boolean maskIp = enforcement.isMaskDeviceIp() || isLmtEnabled;
        final boolean maskDeviceInfo = enforcement.isMaskDeviceInfo() || isLmtEnabled;
        final Device maskedDevice = userFpdTcfMask.maskDevice(device, maskIp, maskGeo, maskDeviceInfo);

        return BidderPrivacyResult.builder()
                .requestBidder(bidder)
                .user(maskedUser)
                .device(maskedDevice)
                .blockedAnalyticsByTcf(blockAnalyticsReport)
                .build();
    }
}
