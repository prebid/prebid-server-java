package org.prebid.server.privacy.model;

import lombok.Value;
import org.prebid.server.privacy.gdpr.model.TcfContext;

@Value(staticConstructor = "of")
public class PrivacyContext {

    Privacy privacy;

    TcfContext tcfContext;

    String ipAddress;

    PrivacyDebugLog privacyDebugLog;

    public static PrivacyContext of(Privacy privacy, TcfContext tcfContext) {
        return of(privacy, tcfContext, null, null);
    }

    public static PrivacyContext from(TcfContext tcfContext, PrivacyResult privacyResult) {
        final Privacy validPrivacy = privacyResult.getValidPrivacy();
        final PrivacyDebugLog privacyDebugLog = PrivacyDebugLog.from(
                privacyResult.getOriginPrivacy(), validPrivacy, tcfContext);

        return PrivacyContext.of(validPrivacy, tcfContext, tcfContext.getIpAddress(), privacyDebugLog);
    }
}
