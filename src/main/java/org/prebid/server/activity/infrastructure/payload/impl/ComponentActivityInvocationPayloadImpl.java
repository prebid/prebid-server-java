package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ComponentActivityInvocationPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class ComponentActivityInvocationPayloadImpl implements ComponentActivityInvocationPayload {

    ComponentType componentType;

    String componentName;
}
