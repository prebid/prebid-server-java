package org.prebid.server.activity.infrastructure.payload;

import org.prebid.server.activity.ComponentType;

public interface ActivityCallPayload {

    ComponentType componentType();

    String componentName();
}
