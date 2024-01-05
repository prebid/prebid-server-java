package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.prebid.server.auction.IpAddressHelper;

public class UserFpdCoppaMask extends UserFpdPrivacyMask {

    public UserFpdCoppaMask(IpAddressHelper ipAddressHelper) {
        super(ipAddressHelper);
    }

    public User maskUser(User user) {
        return maskUser(user, true, true, true);
    }

    public Device maskDevice(Device device) {
        return maskDevice(device, true, true, true);
    }

    @Override
    protected Geo maskGeo(Geo geo) {
        return geo.toBuilder()
                .lat(null)
                .lon(null)
                .metro(null)
                .city(null)
                .zip(null)
                .build();
    }
}
