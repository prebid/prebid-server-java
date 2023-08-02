package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

public interface Rule {

    Result proceed(ActivityCallPayload activityCallPayload);

    enum Result {

        ALLOW,

        DISALLOW,

        ABSTAIN
    }
}
