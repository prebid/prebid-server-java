package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class ActivityInvocationPayloadImpl implements ActivityInvocationPayload {

    ComponentType componentType;

    String componentName;
}
