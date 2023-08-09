package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.debug.USNatModuleEntryLog;

public class USNatDefault implements PrivacyModule, Loggable {

    private static final USNatDefault INSTANCE = new USNatDefault();

    public static PrivacyModule instance() {
        return INSTANCE;
    }

    @Override
    public Result proceed(ActivityInvocationPayload activityInvocationPayload) {
        return Result.ABSTAIN;
    }

    @Override
    public Object asLogEntry() {
        return USNatModuleEntryLog.of(USNatDefault.class.getSimpleName(), "None", Result.ABSTAIN);
    }
}
