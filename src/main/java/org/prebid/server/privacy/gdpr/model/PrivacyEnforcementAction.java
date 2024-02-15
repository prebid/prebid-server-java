package org.prebid.server.privacy.gdpr.model;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Set;

@Builder(toBuilder = true)
@Data
public class PrivacyEnforcementAction {

    boolean removeUserFpd; // user.id, user.buyeruid, user.yob, user.gender, user.keywords, user.kwarray, user.data, user.ext.data

    boolean removeUserIds;  // user.eids

    boolean maskGeo;  // user.geo, device.geo

    boolean maskDeviceIp;  // ip, ipv6

    boolean maskDeviceInfo;  // ifa, macsha1, macmd5, dpidsha1, dpidmd5, didsha1, didmd5

    boolean blockAnalyticsReport;

    boolean blockBidderRequest;

    boolean blockPixelSync;

    Set<String> eidExceptions;

    public static PrivacyEnforcementAction restrictAll() {
        return PrivacyEnforcementAction.builder()
                .removeUserFpd(true)
                .removeUserIds(true)
                .maskGeo(true)
                .maskDeviceIp(true)
                .maskDeviceInfo(true)
                .blockAnalyticsReport(true)
                .blockBidderRequest(true)
                .blockPixelSync(true)
                .eidExceptions(Collections.emptySet())
                .build();
    }

    public static PrivacyEnforcementAction allowAll() {
        return PrivacyEnforcementAction.builder().eidExceptions(Collections.emptySet()).build();
    }
}
