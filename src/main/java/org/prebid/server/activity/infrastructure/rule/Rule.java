package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

public interface Rule {

    Result proceed(ActivityInvocationPayload activityInvocationPayload);

    enum Result {

        ALLOW,

        DISALLOW,

        ABSTAIN
    }
}
