package org.prebid.server.privacy.model;

import lombok.Value;
import org.prebid.server.privacy.gdpr.model.TcfContext;

import java.util.Collections;

@Value(staticConstructor = "of")
public class PrivacyContext {

    Privacy privacy;

    TcfContext tcfContext;

    String ipAddress;

    PrivacyDebugLog privacyDebugLog;

    public static PrivacyContext of(Privacy privacy, TcfContext tcfContext) {
        return of(privacy, tcfContext, null, null);
    }
}
