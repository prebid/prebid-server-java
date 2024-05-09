package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;

import java.util.Collections;
import java.util.Objects;

public class UserFpdActivityMask {

    private final UserFpdTcfMask userFpdTcfMask;

    public UserFpdActivityMask(UserFpdTcfMask userFpdTcfMask) {
        this.userFpdTcfMask = Objects.requireNonNull(userFpdTcfMask);
    }

    public User maskUser(User user,
                         boolean disallowTransmitUfpd,
                         boolean disallowTransmitEids,
                         boolean disallowTransmitGeo) {

        return userFpdTcfMask.maskUser(
                user,
                disallowTransmitUfpd,
                disallowTransmitEids,
                disallowTransmitGeo,
                Collections.emptySet());
    }

    public Device maskDevice(Device device, boolean disallowTransmitUfpd, boolean disallowTransmitGeo) {
        return userFpdTcfMask.maskDevice(device, disallowTransmitGeo, disallowTransmitGeo, disallowTransmitUfpd);
    }
}
