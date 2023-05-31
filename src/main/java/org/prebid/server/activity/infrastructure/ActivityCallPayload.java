package org.prebid.server.activity.infrastructure;

import lombok.Value;
import org.prebid.server.activity.ComponentType;

@Value(staticConstructor = "of")
public class ActivityCallPayload {

    ComponentType componentType;

    String componentName;
}
