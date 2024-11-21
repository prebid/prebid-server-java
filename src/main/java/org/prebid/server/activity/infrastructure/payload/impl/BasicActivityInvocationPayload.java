package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.GeoActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GpcActivityInvocationPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BasicActivityInvocationPayload implements GeoActivityInvocationPayload, GpcActivityInvocationPayload {

    ComponentType componentType;

    String componentName;

    String country;

    String region;

    String gpc;
}
