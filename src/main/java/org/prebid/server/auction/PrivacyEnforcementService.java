package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.util.InetAddressUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.VendorIdResolver;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Service provides masking for OpenRTB user sensitive information.
 */
public class PrivacyEnforcementService {

    private static final String CATCH_ALL_BIDDERS = "*";
    private static final DecimalFormat ROUND_TWO_DECIMALS =
            new DecimalFormat("###.##", DecimalFormatSymbols.getInstance(Locale.US));

    private static final User EMPTY_USER = User.builder().build();
    private static final ExtUser EMPTY_USER_EXT = ExtUser.builder().build();

    private final boolean useGeoLocation;
    private final BidderCatalog bidderCatalog;
    private final TcfDefinerService tcfDefinerService;
    private final Metrics metrics;
    private final JacksonMapper mapper;
    private final boolean ccpaEnforce;

    private final PrivacyExtractor privacyExtractor;

    public PrivacyEnforcementService(BidderCatalog bidderCatalog,
                                     TcfDefinerService tcfDefinerService,
                                     Metrics metrics,
                                     JacksonMapper mapper,
                                     boolean useGeoLocation,
                                     boolean ccpaEnforce) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
        this.useGeoLocation = useGeoLocation;
        this.ccpaEnforce = ccpaEnforce;

        privacyExtractor = new PrivacyExtractor(mapper);
    }

    Future<List<BidderPrivacyResult>> mask(AuctionContext auctionContext,
                                           Map<String, User> bidderToUser,
                                           ExtUser extUser,
                                           List<String> bidders,
                                           BidderAliases aliases) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final Regs regs = bidRequest.getRegs();
        final Device device = bidRequest.getDevice();

        // For now, COPPA masking all values, so we can omit GDPR masking.
        if (isCoppaMaskingRequired(regs)) {
            return Future.succeededFuture(maskCoppa(bidderToUser, device));
        }

        final Privacy privacy = privacyExtractor.validPrivacyFrom(regs, bidRequest.getUser());
        final Ccpa ccpa = privacy.getCcpa();
        updateCcpaMetrics(ccpa);
        final Map<String, BidderPrivacyResult> ccpaResult =
                ccpaResult(bidRequest, account, bidders, aliases, device, bidderToUser, privacy);

        final Set<String> biddersToApplyTcf = new HashSet<>(bidders);
        biddersToApplyTcf.removeAll(ccpaResult.keySet());

        final AccountGdprConfig accountConfig = auctionContext.getAccount().getGdpr();
        final Timeout timeout = auctionContext.getTimeout();
        final MetricName requestType = auctionContext.getRequestTypeMetric();
        return getBidderToEnforcementAction(device, biddersToApplyTcf, aliases, extUser, regs, accountConfig, timeout)
                .map(bidderToEnforcement -> updatePrivacyMetrics(bidderToEnforcement, requestType, device))
                .map(bidderToEnforcement -> getBidderToPrivacyResult(
                        biddersToApplyTcf, bidderToUser, device, bidderToEnforcement))
                .map(gdprResult -> merge(ccpaResult, gdprResult));
    }

    private Map<String, BidderPrivacyResult> ccpaResult(BidRequest bidRequest,
                                                        Account account,
                                                        List<String> bidders,
                                                        BidderAliases aliases,
                                                        Device device,
                                                        Map<String, User> bidderToUser,
                                                        Privacy privacy) {

        if (isCcpaEnforced(privacy.getCcpa(), account)) {
            return maskCcpa(extractCcpaEnforcedBidders(bidders, bidRequest, aliases), device, bidderToUser);
        }

        return Collections.emptyMap();
    }

    public boolean isCcpaEnforced(Ccpa ccpa, Account account) {
        final boolean shouldEnforceCcpa = BooleanUtils.toBooleanDefaultIfNull(account.getEnforceCcpa(), ccpaEnforce);

        return shouldEnforceCcpa && ccpa.isEnforced();
    }

    private Map<String, BidderPrivacyResult> maskCcpa(
            Set<String> biddersToMask, Device device, Map<String, User> bidderToUser) {

        return biddersToMask.stream()
                .collect(Collectors.toMap(Function.identity(),
                        bidder -> BidderPrivacyResult.builder()
                                .requestBidder(bidder)
                                .user(maskCcpaUser(bidderToUser.get(bidder)))
                                .device(maskCcpaDevice(device))
                                .build()));
    }

    private User maskCcpaUser(User user) {
        if (user != null) {
            return nullIfEmpty(user.toBuilder()
                    .id(null)
                    .buyeruid(null)
                    .geo(maskGeoDefault(user.getGeo()))
                    .ext(maskUserExt(user.getExt()))
                    .build());
        }
        return null;
    }

    private static Device maskCcpaDevice(Device device) {
        return device != null
                ? device.toBuilder()
                .ip(maskIpv4(device.getIp()))
                .ipv6(maskIpv6(device.getIpv6(), 1))
                .geo(maskGeoDefault(device.getGeo()))
                .ifa(null)
                .macsha1(null).macmd5(null)
                .dpidsha1(null).dpidmd5(null)
                .didsha1(null).didmd5(null)
                .build()
                : null;
    }

    /**
     * Determines if COPPA is required.
     */
    private static boolean isCoppaMaskingRequired(Regs regs) {
        return regs != null && Objects.equals(regs.getCoppa(), 1);
    }

    private List<BidderPrivacyResult> maskCoppa(Map<String, User> bidderToUser, Device device) {
        metrics.updatePrivacyCoppaMetric();

        return bidderToUser.entrySet().stream()
                .map(bidderAndUser -> BidderPrivacyResult.builder()
                        .requestBidder(bidderAndUser.getKey())
                        .user(maskCoppaUser(bidderAndUser.getValue()))
                        .device(maskCoppaDevice(device))
                        .build())
                .collect(Collectors.toList());
    }

    private User maskCoppaUser(User user) {
        if (user != null) {
            return nullIfEmpty(user.toBuilder()
                    .id(null)
                    .yob(null)
                    .gender(null)
                    .buyeruid(null)
                    .geo(maskGeoForCoppa(user.getGeo()))
                    .ext(maskUserExt(user.getExt()))
                    .build());
        }
        return null;
    }

    private static Device maskCoppaDevice(Device device) {
        return device != null
                ? device.toBuilder()
                .ip(maskIpv4(device.getIp()))
                .ipv6(maskIpv6(device.getIpv6(), 2))
                .geo(maskGeoForCoppa(device.getGeo()))
                .ifa(null)
                .macsha1(null).macmd5(null)
                .dpidsha1(null).dpidmd5(null)
                .didsha1(null).didmd5(null)
                .build()
                : null;
    }

    /**
     * Returns masked for COPPA {@link Geo}.
     */
    private static Geo maskGeoForCoppa(Geo geo) {
        final Geo updatedGeo = geo != null
                ? geo.toBuilder().lat(null).lon(null).metro(null).city(null).zip(null).build()
                : null;
        return updatedGeo == null || updatedGeo.equals(Geo.EMPTY) ? null : updatedGeo;
    }

    /**
     * Returns {@link Future &lt;{@link Map}&lt;{@link String}, {@link PrivacyEnforcementAction}&gt;&gt;},
     * where bidder names mapped to actions for GDPR masking for pbs server.
     */
    private Future<Map<String, PrivacyEnforcementAction>> getBidderToEnforcementAction(
            Device device,
            Set<String> bidders,
            BidderAliases aliases,
            ExtUser extUser,
            Regs regs,
            AccountGdprConfig accountConfig,
            Timeout timeout) {

        final ExtRegs extRegs = extRegs(regs);
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;
        final String gdprAsString = gdpr != null ? gdpr.toString() : null;
        final String gdprConsent = extUser != null ? extUser.getConsent() : null;
        final String ipAddress = useGeoLocation && device != null ? device.getIp() : null;

        final VendorIdResolver vendorIdResolver = VendorIdResolver.of(bidderCatalog, aliases);

        return tcfDefinerService.resultForBidderNames(
                new HashSet<>(bidders),
                vendorIdResolver,
                gdprAsString,
                gdprConsent,
                ipAddress,
                accountConfig,
                timeout)
                .map(tcfResponse -> mapTcfResponseToEachBidder(tcfResponse, bidders));
    }

    /**
     * Extracts {@link ExtRegs} from {@link Regs}.
     */
    private ExtRegs extRegs(Regs regs) {
        final ObjectNode regsExt = regs != null ? regs.getExt() : null;
        if (regsExt != null) {
            try {
                return mapper.mapper().treeToValue(regsExt, ExtRegs.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error decoding bidRequest.regs.ext: %s", e.getMessage()), e);
            }
        }
        return null;
    }

    private Set<String> extractCcpaEnforcedBidders(List<String> bidders, BidRequest bidRequest, BidderAliases aliases) {
        final Set<String> ccpaEnforcedBidders = new HashSet<>(bidders);

        final ExtBidRequest extBidRequest = requestExt(bidRequest);
        final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;
        final List<String> nosaleBidders = extRequestPrebid != null
                ? ListUtils.emptyIfNull(extRequestPrebid.getNosale())
                : Collections.emptyList();

        if (nosaleBidders.size() == 1 && nosaleBidders.contains(CATCH_ALL_BIDDERS)) {
            ccpaEnforcedBidders.clear();
        } else {
            ccpaEnforcedBidders.removeAll(nosaleBidders);
        }

        ccpaEnforcedBidders.removeIf(bidder ->
                !bidderCatalog.bidderInfoByName(aliases.resolveBidder(bidder)).isCcpaEnforced());

        return ccpaEnforcedBidders;
    }

    private Map<String, PrivacyEnforcementAction> mapTcfResponseToEachBidder(
            TcfResponse<String> tcfResponse, Set<String> bidders) {

        final Map<String, PrivacyEnforcementAction> bidderNameToAction = tcfResponse.getActions();
        return bidders.stream().collect(Collectors.toMap(Function.identity(), bidderNameToAction::get));
    }

    private void updateCcpaMetrics(Ccpa ccpa) {
        metrics.updatePrivacyCcpaMetrics(ccpa.isNotEmpty(), ccpa.isEnforced());
    }

    private Map<String, PrivacyEnforcementAction> updatePrivacyMetrics(
            Map<String, PrivacyEnforcementAction> bidderToEnforcement, MetricName requestType, Device device) {

        for (final Map.Entry<String, PrivacyEnforcementAction> bidderEnforcement : bidderToEnforcement.entrySet()) {
            final String bidder = bidderEnforcement.getKey();
            final PrivacyEnforcementAction enforcement = bidderEnforcement.getValue();

            metrics.updateAuctionTcfMetrics(
                    bidder,
                    requestType,
                    enforcement.isRemoveUserIds(),
                    enforcement.isMaskGeo(),
                    enforcement.isBlockBidderRequest(),
                    enforcement.isBlockAnalyticsReport());
        }

        if (isLmtEnabled(device)) {
            metrics.updatePrivacyLmtMetric();
        }

        return bidderToEnforcement;
    }

    /**
     * Returns {@link Map}&lt;{@link String}, {@link BidderPrivacyResult}&gt;, where bidder name mapped to masked
     * {@link BidderPrivacyResult}. Masking depends on GDPR and COPPA.
     */
    private List<BidderPrivacyResult> getBidderToPrivacyResult(
            Set<String> bidders,
            Map<String, User> bidderToUser,
            Device device,
            Map<String, PrivacyEnforcementAction> bidderToEnforcement) {

        final boolean isLmtEnabled = isLmtEnabled(device);
        return bidderToUser.entrySet().stream()
                .filter(entry -> bidders.contains(entry.getKey()))
                .map(bidderUserEntry -> createBidderPrivacyResult(
                        bidderUserEntry.getValue(),
                        device,
                        bidderUserEntry.getKey(),
                        isLmtEnabled,
                        bidderToEnforcement))
                .collect(Collectors.toList());
    }

    /**
     * Returns {@link BidderPrivacyResult} with GDPR masking.
     */
    private BidderPrivacyResult createBidderPrivacyResult(User user,
                                                          Device device,
                                                          String bidder,
                                                          boolean isLmtEnabled,
                                                          Map<String, PrivacyEnforcementAction> bidderToEnforcement) {

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

        final boolean maskGeo = privacyEnforcementAction.isMaskGeo() || isLmtEnabled;
        final boolean maskUserIds = privacyEnforcementAction.isRemoveUserIds() || isLmtEnabled;
        final User maskedUser = maskTcfUser(user, maskUserIds, maskGeo);

        final boolean maskIp = privacyEnforcementAction.isMaskDeviceIp() || isLmtEnabled;
        final boolean maskInfo = privacyEnforcementAction.isMaskDeviceInfo() || isLmtEnabled;
        final Device maskedDevice = maskTcfDevice(device, maskIp, maskGeo, maskInfo);

        return BidderPrivacyResult.builder()
                .requestBidder(bidder)
                .user(maskedUser)
                .device(maskedDevice)
                .blockedAnalyticsByTcf(blockAnalyticsReport)
                .build();
    }

    /**
     * Returns masked {@link User}.
     */
    private User maskTcfUser(User user, boolean maskUserIds, boolean maskGeo) {
        if (user != null) {
            final User.UserBuilder userBuilder = user.toBuilder();

            if (maskGeo) {
                userBuilder.geo(maskGeoDefault(user.getGeo()));
            }

            if (maskUserIds) {
                userBuilder
                        .id(null)
                        .buyeruid(null)
                        .ext(maskUserExt(user.getExt()));
            }

            return nullIfEmpty(userBuilder.build());
        }
        return null;
    }

    /**
     * Returns masked device accordingly for each flag.
     */
    private static Device maskTcfDevice(Device device, boolean maskIp, boolean maskGeo, boolean maskInfo) {
        if (device != null) {
            final Device.DeviceBuilder deviceBuilder = device.toBuilder();
            if (maskIp) {
                deviceBuilder
                        .ip(maskIpv4(device.getIp()))
                        .ipv6(maskIpv6(device.getIpv6(), 1));
            }

            if (maskGeo) {
                deviceBuilder.geo(maskGeoDefault(device.getGeo()));
            }

            if (maskInfo) {
                deviceBuilder.ifa(null)
                        .macsha1(null).macmd5(null)
                        .dpidsha1(null).dpidmd5(null)
                        .didsha1(null).didmd5(null);
            }

            return deviceBuilder.build();
        }
        return null;
    }

    /**
     * Returns masked for GDPR {@link Geo} by rounding lon and lat properties.
     */
    private static Geo maskGeoDefault(Geo geo) {
        return geo != null
                ? geo.toBuilder()
                .lat(maskGeoCoordinate(geo.getLat()))
                .lon(maskGeoCoordinate(geo.getLon()))
                .build()
                : null;
    }

    /**
     * Returns masked geo coordinate with rounded value to two decimals.
     */
    private static Float maskGeoCoordinate(Float coordinate) {
        return coordinate != null ? Float.valueOf(ROUND_TWO_DECIMALS.format(coordinate)) : null;
    }

    /**
     * Returns masked digitrust and eids of user ext.
     */
    private ObjectNode maskUserExt(ObjectNode userExt) {
        try {
            final ExtUser extUser = userExt != null ? mapper.mapper().treeToValue(userExt, ExtUser.class) : null;
            final ExtUser maskedExtUser = extUser != null
                    ? nullIfEmpty(extUser.toBuilder().eids(null).digitrust(null).build())
                    : null;
            return maskedExtUser != null ? mapper.mapper().valueToTree(maskedExtUser) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    /**
     * Returns null if {@link ExtUser} has no data in case of masking was applied.
     */
    private static ExtUser nullIfEmpty(ExtUser userExt) {
        return Objects.equals(userExt, EMPTY_USER_EXT) ? null : userExt;
    }

    /**
     * Returns null if {@link User} has no data in case of masking was applied.
     */
    private static User nullIfEmpty(User user) {
        return Objects.equals(user, EMPTY_USER) ? null : user;
    }

    /**
     * Masks ip v4 address by replacing last group with zero.
     */
    private static String maskIpv4(String ip) {
        return ip != null && InetAddressUtils.isIPv4Address(ip) ? maskIp(ip, ".", 1) : ip;
    }

    /**
     * Masks ip v6 address by replacing last number of groups .
     */
    private static String maskIpv6(String ip, Integer groupsNumber) {
        return ip != null && InetAddressUtils.isIPv6Address(ip) ? maskIp(ip, ":", groupsNumber) : ip;
    }

    /**
     * Masks ip address by replacing bits after last separator with zero.
     */
    private static String maskIp(String ip, String delimiter, int groups) {
        if (StringUtils.isBlank(ip)) {
            return ip;
        }
        String maskedIp = ip;
        for (int i = 0; i < groups; i++) {
            if (maskedIp.contains(delimiter)) {
                maskedIp = maskedIp.substring(0, maskedIp.lastIndexOf(delimiter));
            } else {
                // ip is malformed
                return ip;
            }
        }
        return String.format("%s%s", maskedIp,
                IntStream.range(0, groups).mapToObj(ignored -> "0")
                        .collect(Collectors.joining(delimiter, delimiter, "")));
    }

    private static boolean isLmtEnabled(Device device) {
        return device != null && Objects.equals(device.getLmt(), 1);
    }

    private static List<BidderPrivacyResult> merge(
            Map<String, BidderPrivacyResult> ccpaResult, List<BidderPrivacyResult> gdprResult) {

        final List<BidderPrivacyResult> result = new ArrayList<>(ccpaResult.values());
        result.addAll(gdprResult);
        return result;
    }

    private ExtBidRequest requestExt(BidRequest bidRequest) {
        try {
            return bidRequest.getExt() != null
                    ? mapper.mapper().treeToValue(bidRequest.getExt(), ExtBidRequest.class)
                    : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()), e);
        }
    }
}
