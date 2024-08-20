package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.CompositeActivityInvocationPayload;

public abstract class AbstractMatchRule implements Rule {

    @Override
    public Result proceed(CompositeActivityInvocationPayload payload) {
        if (!matches(payload)) {
            return Result.ABSTAIN;
        }

        return isAllowed() ? Result.ALLOW : Result.DISALLOW;
    }

    public abstract boolean matches(CompositeActivityInvocationPayload payload);

    public abstract boolean isAllowed();
}
