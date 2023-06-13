package org.prebid.server.activity;

import lombok.Value;

@Value(staticConstructor = "of")
public class ActivityContextResult {

    boolean isAllowed;

    int processedRulesCount;
}
