package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class ActivityCallPayloadImpl implements ActivityCallPayload {

    ComponentType componentType;

    String componentName;
}
