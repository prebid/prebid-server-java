package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

public abstract class AbstractMatchRule implements Rule {

    @Override
    public Result proceed(ActivityCallPayload activityCallPayload) {
        if (!matches(activityCallPayload)) {
            return Result.ABSTAIN;
        }

        return allowed() ? Result.ALLOW : Result.DISALLOW;
    }

    public abstract boolean matches(ActivityCallPayload activityCallPayload);

    public abstract boolean allowed();
}
