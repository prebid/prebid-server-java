package org.prebid.server.hooks.v1.exitpoint;

import io.vertx.core.MultiMap;

public interface ExitpointPayload {

    MultiMap responseHeaders();

    String responseBody();
}
