package org.prebid.server.auction.model;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class PrivacyEnforcementResult {

    User user;

    Device device;
}

