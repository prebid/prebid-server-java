package org.prebid.server.activity.infrastructure.privacy;

import org.prebid.server.activity.infrastructure.debug.ActivityDebugUtils;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.rule.AndRule;

import java.util.List;

public class AndPrivacyModules implements PrivacyModule, Loggable {

    private final AndRule and;

    public AndPrivacyModules(List<? extends PrivacyModule> privacyModules) {
        and = new AndRule(privacyModules);
    }

    @Override
    public Result proceed(ActivityInvocationPayload activityInvocationPayload) {
        return and.proceed(activityInvocationPayload);
    }


    @Override
    public Object asLogEntry() {
        return ActivityDebugUtils.asLogEntry(and);
    }
}
