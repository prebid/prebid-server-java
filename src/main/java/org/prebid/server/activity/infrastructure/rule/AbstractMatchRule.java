package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

public abstract class AbstractMatchRule implements Rule {

    @Override
    public Result proceed(ActivityInvocationPayload activityInvocationPayload) {
        if (!matches(activityInvocationPayload)) {
            return Result.ABSTAIN;
        }

        return allowed() ? Result.ALLOW : Result.DISALLOW;
    }

    public abstract boolean matches(ActivityInvocationPayload activityInvocationPayload);

    public abstract boolean allowed();
}
