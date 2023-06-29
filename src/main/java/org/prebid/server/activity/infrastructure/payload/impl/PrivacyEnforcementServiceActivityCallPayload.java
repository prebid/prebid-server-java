package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.GpcActivityCallPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class PrivacyEnforcementServiceActivityCallPayload implements
        GeoActivityCallPayload,
        GpcActivityCallPayload {

    @Delegate
    ActivityCallPayload activityCallPayload;

    String country;

    String region;

    String gpc;
}
