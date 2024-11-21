package org.prebid.server.hooks.v1.exit;

import io.vertx.core.MultiMap;

public interface ExitpointPayload {

    MultiMap responseHeaders();

    String responseBody();
}
