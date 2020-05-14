package org.prebid.server.privacy.gdpr.model;

import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
public class PrivacyEnforcementAction {

    boolean removeUserIds;  // user.buyeruid, user.id, user.ext.eids, user.ext.digitrust

    boolean maskGeo;  // user.geo, device.geo

    boolean maskDeviceIp;  // ip, ipv6

    boolean maskDeviceInfo;  // ifa, macsha1, macmd5, dpidsha1, dpidmd5, didsha1, didmd5

    boolean blockAnalyticsReport;

    boolean blockBidderRequest;

    boolean blockPixelSync;

    public static PrivacyEnforcementAction restrictAll() {
        return PrivacyEnforcementAction.builder()
                .removeUserIds(true)
                .maskGeo(true)
                .maskDeviceIp(true)
                .maskDeviceInfo(true)
                .blockAnalyticsReport(true)
                .blockBidderRequest(true)
                .blockPixelSync(true)
                .build();
    }

    public static PrivacyEnforcementAction allowAll() {
        return PrivacyEnforcementAction.builder().build();
    }
}
