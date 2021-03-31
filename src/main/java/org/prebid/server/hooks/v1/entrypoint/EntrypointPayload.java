package org.prebid.server.hooks.v1.entrypoint;

import io.vertx.core.MultiMap;

public interface EntrypointPayload {

    MultiMap queryParams();

    MultiMap headers();

    String body();
}
