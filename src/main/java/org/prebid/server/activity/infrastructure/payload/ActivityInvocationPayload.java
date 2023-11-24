package org.prebid.server.activity.infrastructure.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.prebid.server.activity.ComponentType;

public interface ActivityInvocationPayload {

    @JsonProperty
    ComponentType componentType();

    @JsonProperty
    String componentName();
}
