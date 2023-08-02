package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GpcActivityInvocationPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class PrivacyEnforcementServiceActivityInvocationPayload implements
        GeoActivityInvocationPayload,
        GpcActivityInvocationPayload {

    @Delegate
    ActivityInvocationPayload activityInvocationPayload;

    String country;

    String region;

    String gpc;
}
