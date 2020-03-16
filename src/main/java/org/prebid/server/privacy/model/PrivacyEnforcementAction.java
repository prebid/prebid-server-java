package org.prebid.server.privacy.model;

import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
public class PrivacyEnforcementAction {

    private static PrivacyEnforcementAction ALLOW_ALL = PrivacyEnforcementAction.builder()
            .removeUserBuyerUid(true)
            .maskGeo(true)
            .maskDeviceIp(true)
            .maskDeviceInfo(true)
            .blockAnalyticsReport(true)
            .blockBidderRequest(true)
            .blockPixelSync(true)
            .build();

    private static PrivacyEnforcementAction RESTRICT_ALL = PrivacyEnforcementAction.builder().build();

    boolean removeUserBuyerUid;

    boolean maskGeo;  // user.geo, device.geo

    boolean maskDeviceIp;  // ip, ipv6

    boolean maskDeviceInfo;  // ifa, macsha1, macmd5, dpidsha1, dpidmd5, didsha1, didmd5

    boolean blockAnalyticsReport;

    boolean blockBidderRequest;

    // TODO
    boolean blockPixelSync;

    public static PrivacyEnforcementAction restrictAll() {
        return RESTRICT_ALL;
    }

    public static PrivacyEnforcementAction allowAll() {
        return ALLOW_ALL;
    }
}
