package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;

public class USNatDefault implements PrivacyModule {

    private static final USNatDefault INSTANCE = new USNatDefault();

    public static PrivacyModule instance() {
        return INSTANCE;
    }

    @Override
    public Result proceed(ActivityInvocationPayload activityInvocationPayload) {
        return Result.ABSTAIN;
    }
}
