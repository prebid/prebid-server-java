package org.prebid.server.auction.model.activity.rule;

import org.prebid.server.auction.model.activity.Activity;
import org.prebid.server.auction.model.activity.payload.ActivityPayload;

public interface Rule {

    boolean matches(Activity activity, ActivityPayload activityPayload);

    boolean allowed();
}
