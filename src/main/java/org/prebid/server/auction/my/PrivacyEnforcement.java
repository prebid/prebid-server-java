package org.prebid.server.auction.my;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.UserDeviceRegs;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PrivacyEnforcement {
    private static final DecimalFormat ROUND_TWO_DECIMALS =
            new DecimalFormat("###.##", DecimalFormatSymbols.getInstance(Locale.US));
    private Gdpr gdpr;
    private GdprService gdprService;

    public Future<Map<String, UserDeviceRegs>> mask(Map<String, User> bidderToUser, Device device, Regs regs, ExtRegs extRegs,

                                                    BidRequest bidRequest, List<String> bidders,
                                                    Map<String, String> aliases,
                                                    String publisherId, ExtUser extUser, Timeout timeout) {

        final boolean coppaMasking = isCoppaMaskingRequired(regs);

        final Integer deviceLmt = device != null ? device.getLmt() : null;

        return gdpr.getVendorsToGdprPermission(bidRequest, bidders, aliases, publisherId, extUser, extRegs, timeout)
                .map(integerBooleanMap ->
                        bidderToUser.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey,
                                        stringUserEntry -> {
                                        //TODO can pull out this createUserDeviceRegs
                                            final User user = stringUserEntry.getValue();
                                            final String bidder = stringUserEntry.getKey();
                                            final boolean gdprMasking = gdpr.isGdprMaskingRequiredFor(bidder, aliases, integerBooleanMap, deviceLmt);

                                            final User maskUser = prepareUser(user, coppaMasking, gdprMasking);
                                            final Device maskDevice = prepareDevice(device, coppaMasking, gdprMasking);
                                            final Regs maskReges = prepareRegs(regs, extRegs, gdprMasking);

                                            return UserDeviceRegs.of(maskUser, maskDevice, maskReges);
                                        }))
                );
    }

    /**
     * Determines if COPPA is required.
     */
    public static boolean isCoppaMaskingRequired(Regs regs) {
        return regs != null && Objects.equals(regs.getCoppa(), 1);
    }

    private static User prepareUser(User user, boolean coppaMaskingRequired, boolean gdprMaskingRequired) {
        //user != null ?
        if (coppaMaskingRequired || gdprMaskingRequired) {
            final User.UserBuilder builder = user.toBuilder();
            if (coppaMaskingRequired) {
                builder
                        .id(null)
                        .yob(null)
                        .gender(null);
            }

            // clean user.buyeruid and user.geo (COPPA and GDPR masking)
            builder
                    .buyeruid(null)
                    .geo(coppaMaskingRequired ? maskGeoForCoppa(user.getGeo()) : maskGeoForGdpr(user.getGeo()));

            return builder.build();
        }
        return user;
    }

    private static Device prepareDevice(Device device, boolean coppaMaskingRequired, boolean gdprMaskingRequired) {
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
     * Sets GDPR value 1, if bidder required GDPR masking, but regs.ext.gdpr is not defined.
     */
    private static Regs prepareRegs(Regs regs, ExtRegs extRegs, boolean gdprMaskingRequired) {
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;

        return gdpr == null && gdprMaskingRequired
                ? Regs.of(regs != null ? regs.getCoppa() : null, Json.mapper.valueToTree(ExtRegs.of(1)))
                : regs;
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
     * Returns masked geo coordinate with rounded value to two decimals.
     */
    private static Float maskGeoCoordinate(Float coordinate) {
        return coordinate != null ? Float.valueOf(ROUND_TWO_DECIMALS.format(coordinate)) : null;
    }
}

