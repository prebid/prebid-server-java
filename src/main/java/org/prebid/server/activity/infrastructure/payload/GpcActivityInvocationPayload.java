package org.prebid.server.activity.infrastructure.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface GpcActivityInvocationPayload extends ActivityInvocationPayload {

    @JsonProperty
    String gpc();
}
