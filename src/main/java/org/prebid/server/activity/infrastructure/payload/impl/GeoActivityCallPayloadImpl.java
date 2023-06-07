package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityCallPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class GeoActivityCallPayloadImpl implements GeoActivityCallPayload {

    @Delegate
    ActivityCallPayload activityCallPayload;

    String country;

    String region;
}
