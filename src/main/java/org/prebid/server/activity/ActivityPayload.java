package org.prebid.server.activity;

import lombok.Value;

@Value(staticConstructor = "of")
public class ActivityPayload {

    ComponentType componentType;

    String componentName;
}
