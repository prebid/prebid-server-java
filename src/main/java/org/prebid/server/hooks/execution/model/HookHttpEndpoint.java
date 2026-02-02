package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.model.Endpoint;

public enum HookHttpEndpoint {

    POST_AUCTION(HttpMethod.POST, Endpoint.openrtb2_auction),
    AMP(HttpMethod.GET, Endpoint.openrtb2_amp),
    VIDEO(HttpMethod.POST, Endpoint.openrtb2_video);

    private final HttpMethod httpMethod;

    private final Endpoint endpoint;

    HookHttpEndpoint(HttpMethod httpMethod, Endpoint endpoint) {
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
    }

    public HttpMethod httpMethod() {
        return httpMethod;
    }

    public Endpoint endpoint() {
        return endpoint;
    }

    @JsonValue
    @Override
    public String toString() {
        return httpMethod.name() + " " + endpoint.value();
    }
}
