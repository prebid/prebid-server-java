package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.prebid.server.auction.IpAddressHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;

public class UserFpdCcpaMask extends UserFpdPrivacyMask {

    public UserFpdCcpaMask(IpAddressHelper ipAddressHelper) {
        super(ipAddressHelper);
    }

    public User maskUser(User user) {
        return maskUser(user, true, true, true, Collections.emptySet());
    }

    public Device maskDevice(Device device) {
        return maskDevice(device, true, true, true);
    }

    @Override
    protected Geo maskGeo(Geo geo) {
        return geo.toBuilder()
                .lat(maskGeoCoordinate(geo.getLat()))
                .lon(maskGeoCoordinate(geo.getLon()))
                .metro(null)
                .city(null)
                .zip(null)
                .build();
    }

    private static Float maskGeoCoordinate(Float coordinate) {
        return coordinate != null
                ? BigDecimal.valueOf(coordinate).setScale(2, RoundingMode.HALF_UP).floatValue()
                : null;
    }
}
