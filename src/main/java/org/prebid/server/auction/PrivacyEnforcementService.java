package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.PrivacyEnforcementResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.response.BidderInfo;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service provides masking for OpenRTB user sensitive information.
 */
public class PrivacyEnforcementService {

    private static final DecimalFormat ROUND_TWO_DECIMALS =
            new DecimalFormat("###.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final User EMPTY_USER = User.builder().build();

    private final GdprService gdprService;
    private final BidderCatalog bidderCatalog;
    private final Metrics metrics;
    private final boolean useGeoLocation;

    public PrivacyEnforcementService(GdprService gdprService, BidderCatalog bidderCatalog, Metrics metrics,
                                     boolean useGeoLocation) {
        this.gdprService = Objects.requireNonNull(gdprService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.metrics = Objects.requireNonNull(metrics);
        this.useGeoLocation = useGeoLocation;
    }

    /**
     * Returns {@link Future &lt;{@link Map}&lt;{@link String}, {@link PrivacyEnforcementResult }&gt;&gt;}, where
     * bidders name mapped to masked {@link PrivacyEnforcementResult}.
     */
    Future<Map<String, PrivacyEnforcementResult>> mask(
            Map<String, User> bidderToUser, ExtUser extUser, List<String> bidders, Map<String, String> aliases,
            BidRequest bidRequest, Boolean isGdprEnforcedByAccount, Timeout timeout) {

        final Regs regs = bidRequest.getRegs();
        final ExtRegs extRegs = extRegs(regs);
        final Device device = bidRequest.getDevice();

        return getVendorsToGdprPermission(device, bidders, aliases, extUser, extRegs, isGdprEnforcedByAccount, timeout)
                .map(vendorToGdprPermission -> getBidderToPrivacyEnforcementResult(bidderToUser, regs, extRegs, device,
                        aliases, vendorToGdprPermission));
    }

    /**
     * Extracts {@link ExtRegs} from {@link Regs}.
     */
    private static ExtRegs extRegs(Regs regs) {
        final ObjectNode regsExt = regs != null ? regs.getExt() : null;
        if (regsExt != null) {
            try {
                return Json.mapper.treeToValue(regsExt, ExtRegs.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error decoding bidRequest.regs.ext: %s", e.getMessage()), e);
            }
        }
        return null;
    }

    /**
     * Determines if COPPA is required.
     */
    private static boolean isCoppaMaskingRequired(Regs regs) {
        return regs != null && Objects.equals(regs.getCoppa(), 1);
    }

    /**
     * Returns {@link Future &lt;{@link Map}&lt;{@link Integer}, {@link Boolean}&gt;&gt;}, where bidders vendor id
     * mapped to enabling or disabling GDPR in scope of pbs server. If bidder vendor id is not present in map,
     * it means that pbs not enforced particular bidder to follow pbs GDPR procedure.
     */
    private Future<Map<Integer, Boolean>> getVendorsToGdprPermission(
            Device device, List<String> bidders, Map<String, String> aliases, ExtUser extUser, ExtRegs extRegs,
            Boolean isGdprEnforcedByAccount, Timeout timeout) {

        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;
        final String gdprAsString = gdpr != null ? gdpr.toString() : null;
        final String gdprConsent = extUser != null ? extUser.getConsent() : null;
        final String ipAddress = useGeoLocation && device != null ? device.getIp() : null;
        final Set<Integer> vendorIds = extractGdprEnforcedVendors(bidders, aliases);

        return gdprService.isGdprEnforced(gdprAsString, isGdprEnforcedByAccount, vendorIds)
                ? gdprService.resultByVendor(vendorIds, gdprAsString, gdprConsent, ipAddress, timeout)
                .map(GdprResponse::getVendorsToGdpr)
                : Future.succeededFuture(Collections.emptyMap());
    }

    /**
     * Extracts GDPR enforced vendor IDs.
     */
    private Set<Integer> extractGdprEnforcedVendors(List<String> bidders, Map<String, String> aliases) {
        return bidders.stream()
                .map(bidder -> bidderCatalog.bidderInfoByName(resolveBidder(bidder, aliases)).getGdpr())
                .filter(BidderInfo.GdprInfo::isEnforced)
                .map(BidderInfo.GdprInfo::getVendorId)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the name associated with bidder if bidder is an alias.
     * If it's not an alias, the bidder is returned.
     */
    private static String resolveBidder(String bidder, Map<String, String> aliases) {
        return aliases.getOrDefault(bidder, bidder);
    }

    /**
     * Returns {@link Map}&lt;{@link String}, {@link PrivacyEnforcementResult}&gt;, where bidder name mapped to masked
     * {@link PrivacyEnforcementResult}. Masking depends on GDPR and COPPA.
     */
    private Map<String, PrivacyEnforcementResult> getBidderToPrivacyEnforcementResult(
            Map<String, User> bidderToUser, Regs regs, ExtRegs extRegs, Device device, Map<String, String> aliases,
            Map<Integer, Boolean> vendorToGdprPermission) {

        final Integer deviceLmt = device != null ? device.getLmt() : null;
        final boolean coppaMasking = isCoppaMaskingRequired(regs);
        return bidderToUser.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        bidderUserEntry -> createPrivacyEnforcementResult(bidderUserEntry.getValue(), device, regs,
                                extRegs, bidderUserEntry.getKey(), aliases, deviceLmt, coppaMasking,
                                vendorToGdprPermission)));
    }

    /**
     * Returns {@link PrivacyEnforcementResult} with GDPR and COPPA masking.
     */
    private PrivacyEnforcementResult createPrivacyEnforcementResult(
            User user, Device device, Regs regs, ExtRegs extRegs, String bidder, Map<String, String> aliases,
            Integer deviceLmt, boolean coppaMasking, Map<Integer, Boolean> vendorToGdprPermission) {

        final boolean gdprMasking = isGdprMaskingRequiredFor(bidder, aliases, deviceLmt, vendorToGdprPermission);

        final User maskedUser = maskUser(user, coppaMasking, gdprMasking);
        final Device maskedDevice = maskDevice(device, coppaMasking, gdprMasking);
        final Regs updatedRegs = updateRegs(regs, extRegs, gdprMasking);
        return PrivacyEnforcementResult.of(maskedUser, maskedDevice, updatedRegs);
    }

    /**
     * Returns flag if GDPR masking is required for bidder.
     */
    private boolean isGdprMaskingRequiredFor(String bidder, Map<String, String> aliases, Integer deviceLmt,
                                             Map<Integer, Boolean> vendorToGdprPermission) {
        final boolean maskingRequired;
        final boolean isLmtEnabled = deviceLmt != null && deviceLmt.equals(1);
        if (vendorToGdprPermission.isEmpty() && !isLmtEnabled) {
            maskingRequired = false;
        } else {
            final String resolvedBidderName = resolveBidder(bidder, aliases);
            final int vendorId = bidderCatalog.bidderInfoByName(resolvedBidderName).getGdpr().getVendorId();
            final Boolean gdprAllowsUserData = vendorToGdprPermission.get(vendorId);

            // if bidder was not found in vendorToGdprPermission, it means that it was not enforced for GDPR,
            // so request for this bidder should be sent without changes
            maskingRequired = (gdprAllowsUserData != null && !gdprAllowsUserData) || isLmtEnabled;
            if (maskingRequired) {
                metrics.updateGdprMaskedMetric(resolvedBidderName);
            }
        }
        return maskingRequired;
    }

    /**
     * Returns masked {@link User} if COPPA or GDPR masking applied.
     */
    private static User maskUser(User user, boolean coppaMaskingRequired, boolean gdprMaskingRequired) {
        if (user != null && (coppaMaskingRequired || gdprMaskingRequired)) {
            final User.UserBuilder builder = user.toBuilder();
            if (coppaMaskingRequired) {
                builder
                        .id(null)
                        .yob(null)
                        .gender(null);
            }

            builder
                    .buyeruid(null)
                    .geo(coppaMaskingRequired ? maskGeoForCoppa(user.getGeo()) : maskGeoForGdpr(user.getGeo()));

            return nullIfEmpty(builder.build());
        }
        return user;
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
     * Returns masked for GDPR {@link Geo} by rounding lon and lat properties.
     */
    private static Geo maskGeoForGdpr(Geo geo) {
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
     * Returns null if {@link User} has no data in case of masking was applied.
     */
    private static User nullIfEmpty(User user) {
        return Objects.equals(user, EMPTY_USER) ? null : user;
    }

    /**
     * Returns masked device with masked ipv4, ipv6 and geo.
     */
    private static Device maskDevice(Device device, boolean coppaMaskingRequired, boolean gdprMaskingRequired) {
        return device != null && (coppaMaskingRequired || gdprMaskingRequired)
                ? device.toBuilder()
                .ip(maskIpv4(device.getIp()))
                .ipv6(maskIpv6(device.getIpv6()))
                .geo(coppaMaskingRequired ? maskGeoForCoppa(device.getGeo()) : maskGeoForGdpr(device.getGeo()))
                .ifa(null)
                .macsha1(null).macmd5(null)
                .dpidsha1(null).dpidmd5(null)
                .didsha1(null).didmd5(null)
                .build()
                : device;
    }

    /**
     * Masks ip v4 address by replacing last group with zero.
     */
    private static String maskIpv4(String ip) {
        return maskIp(ip, '.');
    }

    /**
     * Masks ip v6 address by replacing last group with zero.
     */
    private static String maskIpv6(String ip) {
        return maskIp(ip, ':');
    }

    /**
     * Masks ip address by replacing bits after last separator with zero.
     */
    private static String maskIp(String ip, char delimiter) {
        return StringUtils.isNotEmpty(ip) ? ip.substring(0, ip.lastIndexOf(delimiter) + 1) + "0" : ip;
    }

    /**
     * Sets GDPR value 1, if bidder required GDPR masking, but regs.ext.gdpr is not defined.
     */
    private static Regs updateRegs(Regs regs, ExtRegs extRegs, boolean gdprMaskingRequired) {
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;

        return gdpr == null && gdprMaskingRequired
                ? Regs.of(regs != null ? regs.getCoppa() : null, Json.mapper.valueToTree(ExtRegs.of(1)))
                : regs;
    }
}
