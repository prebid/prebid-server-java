package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.prebid.server.auction.IpAddressHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class UserFpdTcfMask extends UserFpdPrivacyMask {

    public UserFpdTcfMask(IpAddressHelper ipAddressHelper) {
        super(ipAddressHelper);
    }

    public User maskUser(User user, boolean maskUserFpd, boolean maskEids, boolean maskGeo) {
        return super.maskUser(user, maskUserFpd, maskEids, maskGeo);
    }

    public Device maskDevice(Device device, boolean maskIp, boolean maskGeo, boolean maskDeviceInfo) {
        return super.maskDevice(device, maskIp, maskGeo, maskDeviceInfo);
    }

    @Override
    protected Geo maskGeo(Geo geo) {
        return geo != null
                ? geo.toBuilder()
                .lat(maskGeoCoordinate(geo.getLat()))
                .lon(maskGeoCoordinate(geo.getLon()))
                .build()
                : null;
    }

    private static Float maskGeoCoordinate(Float coordinate) {
        return coordinate != null
                ? BigDecimal.valueOf(coordinate).setScale(2, RoundingMode.DOWN).floatValue()
                : null;
    }
}
