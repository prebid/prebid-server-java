package org.prebid.server.hooks.v1.endpoint;

import io.vertx.core.MultiMap;

public interface EndpointPayload {

    MultiMap queryParams();

    MultiMap headers();

    String body();
}
