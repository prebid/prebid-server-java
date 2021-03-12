package org.prebid.server.auction;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PrivacyAnonymizationService {

    private static final DecimalFormat ROUND_TWO_DECIMALS =
            new DecimalFormat("###.##", DecimalFormatSymbols.getInstance(Locale.US));

    private static final String TCF = "TCF";
    private static final String CCPA = "CCPA";
    private static final String COPPA = "COPPA";
    private static final String DEPRECATED_BIDDER_LOG = "Bidder %s was deprecated by %s privacy policy.";
    private static final String DEPRECATED_ANALYTICS = "Bidder %s analytics was deprecated by %s privacy policy.";

    private static final ExtUser EMPTY_USER_EXT = ExtUser.builder().build();
    private static final User EMPTY_USER = User.builder().build();
    private static final Device EMPTY_DEVICE = Device.builder().build();

    private final boolean lmtEnforced;
    private final IpAddressHelper ipAddressHelper;
    private final Metrics metrics;
    private final DeviceAnonymizer coppaDeviceAnonymizer;
    private final UserAnonymizer coppaUserAnonymizer;
    private final DeviceAnonymizer ccpaDeviceAnonymizer;
    private final UserAnonymizer ccpaUserAnonymizer;
    private final Set<String> coppaDebugLogs;
    private final Set<String> ccpaDebugLogs;

    public PrivacyAnonymizationService(boolean lmtEnforced, IpAddressHelper ipAddressHelper, Metrics metrics) {
        this.lmtEnforced = lmtEnforced;
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.metrics = Objects.requireNonNull(metrics);
        this.coppaDeviceAnonymizer = makeCoppaDeviceAnonymizer();
        this.coppaUserAnonymizer = makeCoppaUserAnonymizer();
        this.ccpaDeviceAnonymizer = makeCcpaDeviceAnonymizer();
        this.ccpaUserAnonymizer = makeCcpaUserAnonymizer();
        this.coppaDebugLogs = collectLogs(coppaDeviceAnonymizer, coppaUserAnonymizer);
        this.ccpaDebugLogs = collectLogs(ccpaDeviceAnonymizer, ccpaUserAnonymizer);
    }

    private UserAnonymizer makeCcpaUserAnonymizer() {
        return new UserAnonymizer(CCPA)
                .maskGeoCoordinates()
                .cleanDemographics()
                .cleanIds();
    }

    private DeviceAnonymizer makeCcpaDeviceAnonymizer() {
        return new DeviceAnonymizer(CCPA)
                .maskIp()
                .maskGeoCoordinates()
                .cleanIds();
    }

    private UserAnonymizer makeCoppaUserAnonymizer() {
        return new UserAnonymizer(COPPA)
                .cleanGeo()
                .cleanDemographics()
                .cleanIds();
    }

    private DeviceAnonymizer makeCoppaDeviceAnonymizer() {
        return new DeviceAnonymizer(COPPA)
                .maskIp()
                .cleanGeo()
                .cleanIds();
    }

    private Set<String> collectLogs(DeviceAnonymizer deviceAnonymizer, UserAnonymizer userAnonymizer) {
        return Stream.concat(deviceAnonymizer.getLog().stream(), userAnonymizer.getLog().stream())
                .collect(Collectors.toSet());
    }

    public BidderPrivacyResult maskCoppa(User user, Device device, String bidder) {
        return BidderPrivacyResult.builder()
                .device(coppaDeviceAnonymizer.mask(device))
                .user(coppaUserAnonymizer.mask(user))
                .requestBidder(bidder)
                .debugLog(coppaDebugLogs)
                .build();
    }

    public BidderPrivacyResult maskCcpa(User user, Device device, String bidder) {
        return BidderPrivacyResult.builder()
                .device(ccpaDeviceAnonymizer.mask(device))
                .user(ccpaUserAnonymizer.mask(user))
                .requestBidder(bidder)
                .debugLog(ccpaDebugLogs)
                .build();
    }

    public BidderPrivacyResult maskTcf(User user,
                                       Device device,
                                       String bidder,
                                       BidderAliases aliases,
                                       MetricName requestType,
                                       PrivacyEnforcementAction privacyEnforcementAction) {
        final Set<String> tcfLog = new HashSet<>();
        final boolean blockBidderRequest = privacyEnforcementAction.isBlockBidderRequest();
        final boolean blockAnalyticsReport = privacyEnforcementAction.isBlockAnalyticsReport();

        updatePrivacyMetrics(bidder, privacyEnforcementAction, aliases, requestType, user, device);

        if (blockAnalyticsReport) {
            tcfLog.add(String.format(DEPRECATED_ANALYTICS, bidder, TCF));
        }

        if (blockBidderRequest) {
            tcfLog.add(String.format(DEPRECATED_BIDDER_LOG, bidder, TCF));
            return BidderPrivacyResult.builder()
                    .requestBidder(bidder)
                    .blockedRequestByTcf(true)
                    .blockedAnalyticsByTcf(blockAnalyticsReport)
                    .debugLog(tcfLog)
                    .build();
        }

        final boolean isLmtEnabled = device != null && Objects.equals(device.getLmt(), 1) && lmtEnforced;
        final boolean maskGeo = privacyEnforcementAction.isMaskGeo() || isLmtEnabled;
        final boolean maskUserIds = privacyEnforcementAction.isRemoveUserIds() || isLmtEnabled;
        final UserAnonymizer userAnonymizer = createTCFUserAnonymizer(maskUserIds, maskGeo);

        final boolean maskIp = privacyEnforcementAction.isMaskDeviceIp() || isLmtEnabled;
        final boolean maskInfo = privacyEnforcementAction.isMaskDeviceInfo() || isLmtEnabled;
        final DeviceAnonymizer deviceAnonymizer = createTCFDeviceAnonymizer(maskIp, maskInfo, maskGeo);

        final Set<String> maskLog = collectLogs(deviceAnonymizer, userAnonymizer);
        tcfLog.addAll(maskLog);

        return BidderPrivacyResult.builder()
                .requestBidder(bidder)
                .user(userAnonymizer.mask(user))
                .device(deviceAnonymizer.mask(device))
                .blockedAnalyticsByTcf(blockAnalyticsReport)
                .debugLog(tcfLog)
                .build();
    }

    private void updatePrivacyMetrics(
            String bidder,
            PrivacyEnforcementAction privacyEnforcementAction,
            BidderAliases aliases,
            MetricName requestType,
            User user,
            Device device) {

        // Metrics should represent real picture of the bidding process, so if bidder request is blocked
        // by privacy then no reason to increment another metrics, like geo masked, etc.
        final boolean requestBlocked = privacyEnforcementAction.isBlockBidderRequest();
        boolean userIdRemoved = privacyEnforcementAction.isRemoveUserIds();
        if (requestBlocked || (userIdRemoved && !shouldMaskUser(user))) {
            userIdRemoved = false;
        }

        boolean geoMasked = privacyEnforcementAction.isMaskGeo();
        if (requestBlocked || (geoMasked && !shouldMaskGeo(user, device))) {
            geoMasked = false;
        }

        final boolean analyticsBlocked = !requestBlocked && privacyEnforcementAction.isBlockAnalyticsReport();

        metrics.updateAuctionTcfMetrics(
                aliases.resolveBidder(bidder),
                requestType,
                userIdRemoved,
                geoMasked,
                analyticsBlocked,
                requestBlocked);
    }

    /**
     * Returns true if {@link User} has sensitive privacy information that can be masked.
     */
    private static boolean shouldMaskUser(User user) {
        if (user == null) {
            return false;
        }
        if (user.getId() != null || user.getBuyeruid() != null) {
            return true;
        }
        final ExtUser extUser = user.getExt();
        return extUser != null && (CollectionUtils.isNotEmpty(extUser.getEids()));
    }

    /**
     * Returns true if {@link User} or {@link Device} has {@link Geo} information that can be masked.
     */
    private static boolean shouldMaskGeo(User user, Device device) {
        return (user != null && user.getGeo() != null) || (device != null && device.getGeo() != null);
    }

    private UserAnonymizer createTCFUserAnonymizer(boolean isRemovedUserIds, boolean isMaskGeo) {
        final UserAnonymizer userAnonymizer = new UserAnonymizer(TCF);
        if (isRemovedUserIds) {
            userAnonymizer.cleanIds();
        }
        if (isMaskGeo) {
            userAnonymizer.maskGeoCoordinates();
        }
        return userAnonymizer;
    }

    private DeviceAnonymizer createTCFDeviceAnonymizer(boolean isMaskIp, boolean maskInfo,
                                                       boolean isMaskGeo) {
        final DeviceAnonymizer deviceAnonymizer = new DeviceAnonymizer(TCF);
        if (isMaskIp) {
            deviceAnonymizer.maskIp();
        }
        if (isMaskGeo) {
            deviceAnonymizer.maskGeoCoordinates();
        }
        if (maskInfo) {
            deviceAnonymizer.cleanIds();
        }
        return deviceAnonymizer;
    }

    private static Geo cleanGeo(Geo geo) {
        return geo != null
                ? geo.toBuilder()
                .lat(null).lon(null)
                .metro(null).city(null).zip(null)
                .build()
                : null;
    }

    private static Geo maskGeoCoordinates(Geo geo) {
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

    class DeviceAnonymizer {

        private final String enforcementPolicy;
        private final Set<BiFunction<Device, Device.DeviceBuilder, Device.DeviceBuilder>> maskFunctions;
        private final Set<String> logMessages;

        DeviceAnonymizer(String enforcementPolicy) {
            this.enforcementPolicy = enforcementPolicy;
            this.maskFunctions = new HashSet<>();
            this.logMessages = new HashSet<>();
        }

        public DeviceAnonymizer cleanIds() {
            maskFunctions.add((device, deviceBuilder) -> deviceBuilder.ifa(null).macsha1(null).macmd5(null)
                    .dpidsha1(null).dpidmd5(null).didsha1(null).didmd5(null));
            logMessages.add(String.format("Device ids were removed form request to bidder according to %s policy.",
                    enforcementPolicy));
            return this;
        }

        private DeviceAnonymizer maskIp() {
            maskFunctions.add((device, deviceBuilder) -> deviceBuilder.ip(ipAddressHelper.maskIpv4(device.getIp())));
            maskFunctions.add((device, deviceBuilder) -> deviceBuilder.ipv6(ipAddressHelper
                    .anonymizeIpv6(device.getIpv6())));
            logMessages.add(String.format("Device IPs were masked in request to bidder according to %s policy.",
                    enforcementPolicy));
            return this;
        }

        public DeviceAnonymizer maskGeoCoordinates() {
            maskFunctions.add((device, deviceBuilder) ->
                    deviceBuilder.geo(PrivacyAnonymizationService.maskGeoCoordinates(device.getGeo())));
            logMessages.add(String.format("Geolocation was masked in request to bidder according"
                    + " to %s policy.", enforcementPolicy));
            return this;
        }

        public DeviceAnonymizer cleanGeo() {
            maskFunctions.add((device, deviceBuilder) -> deviceBuilder.geo(PrivacyAnonymizationService
                    .cleanGeo(device.getGeo())));
            logMessages.add(String.format("Geolocation and address were removed "
                    + "from request to bidder according to %s policy.", enforcementPolicy));
            return this;
        }

        public Device mask(Device device) {
            if (device == null) {
                return null;
            }
            final Device.DeviceBuilder deviceBuilder = device.toBuilder();
            maskFunctions.forEach(deviceMaskOperator -> deviceMaskOperator.apply(device, deviceBuilder));
            final Device resolvedDevice = deviceBuilder.build();
            return resolvedDevice.equals(EMPTY_DEVICE) ? null : resolvedDevice;
        }

        public Set<String> getLog() {
            return logMessages;
        }
    }

    class UserAnonymizer {
        private final String enforcementPolicy;
        private final Set<BiFunction<User, User.UserBuilder, User.UserBuilder>> maskFunctions;
        private final Set<String> logMessages;

        UserAnonymizer(String enforcementPolicy) {
            this.enforcementPolicy = enforcementPolicy;
            this.maskFunctions = new HashSet<>();
            this.logMessages = new HashSet<>();
        }

        private UserAnonymizer cleanDemographics() {
            maskFunctions.add((user, userBuilder) -> userBuilder.yob(null).gender(null));
            logMessages.add(String.format("User demographics were removed from request to bidder"
                    + " according to %s policy.", enforcementPolicy));
            return this;
        }

        private UserAnonymizer cleanIds() {
            maskFunctions.add((user, userBuilder) -> userBuilder.id(null).buyeruid(null)
                    .ext(maskUserExt(user.getExt())));
            logMessages.add(String.format("User ids were removed from request to bidder according"
                    + " to %s policy.", enforcementPolicy));
            return this;
        }

        private UserAnonymizer maskGeoCoordinates() {
            maskFunctions.add((user, userBuilder) -> userBuilder.geo(PrivacyAnonymizationService
                    .maskGeoCoordinates(user.getGeo())));
            logMessages.add(String.format("Geolocation was masked in request to bidder according"
                    + " to %s policy.", enforcementPolicy));
            return this;
        }

        private UserAnonymizer cleanGeo() {
            maskFunctions.add((user, userBuilder) -> userBuilder.geo(PrivacyAnonymizationService
                    .cleanGeo(user.getGeo())));
            logMessages.add(String.format("Geolocation and address were removed "
                    + "from request to bidder according to %s policy.", enforcementPolicy));
            return this;
        }

        private ExtUser maskUserExt(ExtUser userExt) {
            return userExt != null
                    ? nullIfEmpty(userExt.toBuilder().eids(null).build())
                    : null;
        }

        private ExtUser nullIfEmpty(ExtUser userExt) {
            return Objects.equals(userExt, EMPTY_USER_EXT) ? null : userExt;
        }

        public User mask(User user) {
            if (user == null) {
                return null;
            }
            final User.UserBuilder userBuilder = user.toBuilder();
            maskFunctions.forEach(userMaskOperator -> userMaskOperator.apply(user, userBuilder));
            final User resolvedUser = userBuilder.build();
            return resolvedUser.equals(EMPTY_USER) ? null : resolvedUser;
        }

        public Set<String> getLog() {
            return logMessages;
        }
    }
}
