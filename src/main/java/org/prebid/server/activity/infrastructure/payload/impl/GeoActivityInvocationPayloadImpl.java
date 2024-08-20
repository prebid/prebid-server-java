package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.activity.infrastructure.payload.GeoActivityInvocationPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class GeoActivityInvocationPayloadImpl implements GeoActivityInvocationPayload {

    String country;

    String region;
}
