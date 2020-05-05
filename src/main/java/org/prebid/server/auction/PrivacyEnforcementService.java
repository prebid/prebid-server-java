package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.AccountGdprConfig;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Service provides masking for OpenRTB user sensitive information.
 */
public class PrivacyEnforcementService {

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
        final Regs regs = bidRequest.getRegs();
        final Device device = bidRequest.getDevice();

        // For now, COPPA and CCPA masking all values, so we can omit GDPR masking.
        if (isCoppaMaskingRequired(regs)) {
            return Future.succeededFuture(maskCoppa(bidderToUser, device));
        }

        final Privacy privacy = privacyExtractor.validPrivacyFrom(regs, bidRequest.getUser());
        final Ccpa ccpa = privacy.getCcpa();
        updateCcpaMetrics(ccpa);
        if (isCcpaEnforced(ccpa)) {
            return maskCcpa(bidderToUser, device);
        }

        final AccountGdprConfig accountConfig = auctionContext.getAccount().getGdpr();
        final Timeout timeout = auctionContext.getTimeout();
        final MetricName requestType = auctionContext.getRequestTypeMetric();
        return getBidderToEnforcementAction(device, bidders, aliases, extUser, regs, accountConfig, timeout)
                .map(bidderToEnforcement -> updatePrivacyMetrics(bidderToEnforcement, aliases, requestType, device))
                .map(bidderToEnforcement -> getBidderToPrivacyResult(bidderToUser, device, bidderToEnforcement));
    }

    public boolean isCcpaEnforced(Ccpa ccpa) {
        return ccpaEnforce && ccpa.isCCPAEnforced();
    }

    private Future<List<BidderPrivacyResult>> maskCcpa(Map<String, User> bidderToUser, Device device) {
        return Future.succeededFuture(bidderToUser.entrySet().stream()
                .map(bidderAndUser -> BidderPrivacyResult.builder()
                        .requestBidder(bidderAndUser.getKey())
                        .user(maskCcpaUser(bidderAndUser.getValue()))
                        .device(maskCcpaDevice(device))
                        .build())
                .collect(Collectors.toList()));
    }

    private static User maskCcpaUser(User user) {
        if (user != null) {
            return nullIfEmpty(user.toBuilder()
                    .buyeruid(null)
                    .geo(maskGeoDefault(user.getGeo()))
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

    private static User maskCoppaUser(User user) {
        if (user != null) {
            return nullIfEmpty(user.toBuilder()
                    .id(null)
                    .yob(null)
                    .gender(null)
                    .buyeruid(null)
                    .geo(maskGeoForCoppa(user.getGeo()))
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
            List<String> bidders,
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

    private Map<String, PrivacyEnforcementAction> mapTcfResponseToEachBidder(
            TcfResponse<String> tcfResponse, List<String> bidders) {

        final Map<String, PrivacyEnforcementAction> bidderNameToAction = tcfResponse.getActions();
        return bidders.stream().collect(Collectors.toMap(Function.identity(), bidderNameToAction::get));
    }

    private void updateCcpaMetrics(Ccpa ccpa) {
        metrics.updatePrivacyCcpaMetrics(ccpa.isNotEmpty(), ccpa.isCCPAEnforced());
    }

    private Map<String, PrivacyEnforcementAction> updatePrivacyMetrics(
            Map<String, PrivacyEnforcementAction> bidderToEnforcement,
            BidderAliases aliases,
            MetricName requestType,
            Device device) {

        for (final Map.Entry<String, PrivacyEnforcementAction> bidderEnforcement : bidderToEnforcement.entrySet()) {
            final String bidder = aliases.resolveBidder(bidderEnforcement.getKey());
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
            Map<String, User> bidderToUser, Device device, Map<String, PrivacyEnforcementAction> bidderToEnforcement) {

        final boolean isLmtEnabled = isLmtEnabled(device);
        return bidderToUser.entrySet().stream()
                .map(bidderUserEntry -> createBidderPrivacyResult(bidderUserEntry.getValue(), device,
                        bidderUserEntry.getKey(), isLmtEnabled, bidderToEnforcement))
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
        return maskIp(ip, ".", 1);
    }

    /**
     * Masks ip v6 address by replacing last number of groups .
     */
    private static String maskIpv6(String ip, Integer groupsNumber) {
        return maskIp(ip, ":", groupsNumber);
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
            maskedIp = maskedIp.substring(0, maskedIp.lastIndexOf(delimiter));
        }
        return String.format("%s%s", maskedIp,
                IntStream.range(0, groups).mapToObj(ignored -> "0")
                        .collect(Collectors.joining(delimiter, delimiter, "")));
    }

    private static boolean isLmtEnabled(Device device) {
        return device != null && Objects.equals(device.getLmt(), 1);
    }
}
