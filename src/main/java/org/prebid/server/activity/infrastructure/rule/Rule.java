package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

public interface Rule {

    boolean matches(ActivityCallPayload activityCallPayload);

    boolean allowed();
}
