package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class UserFpdPrivacyMask {

    private final IpAddressHelper ipAddressHelper;

    protected UserFpdPrivacyMask(IpAddressHelper ipAddressHelper) {
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
    }

    protected User maskUser(User user,
                            boolean maskUserFpd,
                            boolean maskEids,
                            Set<String> eidExceptions) {

        if (user == null || !(maskUserFpd || maskEids)) {
            return user;
        }

        final User.UserBuilder userBuilder = user.toBuilder();
        if (maskUserFpd) {
            userBuilder
                    .id(null)
                    .buyeruid(null)
                    .yob(null)
                    .gender(null)
                    .keywords(null)
                    .kwarray(null)
                    .data(null)
                    .geo(null)
                    .ext(maskExtUser(user.getExt()));
        }

        if (maskEids) {
            userBuilder.eids(removeEids(user.getEids(), eidExceptions));
        }

        return nullIfEmpty(userBuilder.build());
    }

    private static ExtUser maskExtUser(ExtUser extUser) {
        return extUser != null
                ? nullIfEmpty(extUser.toBuilder().data(null).build())
                : null;
    }

    private static List<Eid> removeEids(List<Eid> eids, Set<String> exceptions) {
        if (exceptions.isEmpty() || CollectionUtils.isEmpty(eids)) {
            return null;
        }

        final List<Eid> clearedEids = eids.stream()
                .filter(Objects::nonNull)
                .filter(eid -> exceptions.contains(eid.getSource()))
                .toList();

        return clearedEids.isEmpty() ? null : clearedEids;
    }

    protected Device maskDevice(Device device, boolean maskIp, boolean maskGeo, boolean maskDeviceInfo) {
        if (device == null || !(maskIp || maskGeo || maskDeviceInfo)) {
            return device;
        }

        final Device.DeviceBuilder deviceBuilder = device.toBuilder();
        if (maskIp) {
            deviceBuilder
                    .ip(ipAddressHelper.maskIpv4(device.getIp()))
                    .ipv6(ipAddressHelper.anonymizeIpv6(device.getIpv6()));
        }

        if (maskGeo) {
            deviceBuilder.geo(maskNullableGeo(device.getGeo()));
        }

        if (maskDeviceInfo) {
            deviceBuilder.ifa(null)
                    .macsha1(null).macmd5(null)
                    .dpidsha1(null).dpidmd5(null)
                    .didsha1(null).didmd5(null);
        }

        return deviceBuilder.build();
    }

    private static Geo maskNullableGeo(Geo geo) {
        return geo != null ? nullIfEmpty(maskGeo(geo)) : null;
    }

    private static Geo maskGeo(Geo geo) {
        return geo.toBuilder()
                .lat(maskGeoCoordinate(geo.getLat()))
                .lon(maskGeoCoordinate(geo.getLon()))
                .metro(null)
                .city(null)
                .zip(null)
                .accuracy(null)
                .ipservice(null)
                .ext(null)
                .build();
    }

    private static Float maskGeoCoordinate(Float coordinate) {
        return coordinate != null
                ? BigDecimal.valueOf(coordinate).setScale(2, RoundingMode.HALF_UP).floatValue()
                : null;
    }

    private static User nullIfEmpty(User user) {
        return user.equals(User.EMPTY) ? null : user;
    }

    private static Geo nullIfEmpty(Geo geo) {
        return geo.equals(Geo.EMPTY) ? null : geo;
    }

    private static ExtUser nullIfEmpty(ExtUser userExt) {
        return userExt.isEmpty() ? null : userExt;
    }
}
