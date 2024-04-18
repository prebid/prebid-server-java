package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import org.prebid.server.auction.IpAddressHelper;

import java.util.Set;

public class UserFpdTcfMask extends UserFpdPrivacyMask {

    public UserFpdTcfMask(IpAddressHelper ipAddressHelper) {
        super(ipAddressHelper);
    }

    public User maskUser(User user, boolean maskUserFpd, boolean maskEids, Set<String> eidExceptions) {
        return super.maskUser(user, maskUserFpd, maskEids, eidExceptions);
    }

    public Device maskDevice(Device device, boolean maskIp, boolean maskGeo, boolean maskDeviceInfo) {
        return super.maskDevice(device, maskIp, maskGeo, maskDeviceInfo);
    }
}
