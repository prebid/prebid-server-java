package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.Objects;

public abstract class UserFpdPrivacyMask {

    private final IpAddressHelper ipAddressHelper;

    protected UserFpdPrivacyMask(IpAddressHelper ipAddressHelper) {
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
    }

    protected User maskUser(User user, boolean maskUserFpd, boolean maskEids, boolean maskGeo) {
        if (user == null || !(maskUserFpd || maskEids || maskGeo)) {
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
                    .ext(maskExtUser(user.getExt()));
        }

        if (maskEids) {
            userBuilder.eids(null);
        }

        if (maskGeo) {
            userBuilder.geo(maskNullableGeo(user.getGeo()));
        }

        return nullIfEmpty(userBuilder.build());
    }

    private static ExtUser maskExtUser(ExtUser extUser) {
        return extUser != null
                ? nullIfEmpty(extUser.toBuilder().data(null).build())
                : null;
    }

    private Geo maskNullableGeo(Geo geo) {
        return geo != null ? nullIfEmpty(maskGeo(geo)) : null;
    }

    protected abstract Geo maskGeo(Geo geo);

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
