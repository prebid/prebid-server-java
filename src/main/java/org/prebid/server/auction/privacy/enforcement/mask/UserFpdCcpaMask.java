package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;

import java.util.Objects;

public class UserFpdCcpaMask {

    private final UserFpdActivityMask userFpdActivityMask;

    public UserFpdCcpaMask(UserFpdActivityMask userFpdActivityMask) {
        this.userFpdActivityMask = Objects.requireNonNull(userFpdActivityMask);
    }

    public User maskUser(User user) {
        return userFpdActivityMask.maskUser(user, true, true);
    }

    public Device maskDevice(Device device) {
        return userFpdActivityMask.maskDevice(device, true, true);
    }
}
