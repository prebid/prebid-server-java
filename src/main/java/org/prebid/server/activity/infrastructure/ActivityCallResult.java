package org.prebid.server.activity.infrastructure;

import lombok.Value;

@Value(staticConstructor = "of")
public class ActivityCallResult {

    boolean isAllowed;

    int processedRulesCount;
}
