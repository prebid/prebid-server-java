package org.prebid.server.activity.rule;

import org.prebid.server.activity.ActivityPayload;

public interface Rule {

    boolean matches(ActivityPayload activityPayload);

    boolean allowed();
}
