package org.prebid.server.activity.infrastructure.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface GeoActivityInvocationPayload extends ActivityInvocationPayload {

    @JsonProperty
    String country();

    @JsonProperty
    String region();
}
