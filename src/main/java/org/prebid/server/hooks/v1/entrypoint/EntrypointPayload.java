package org.prebid.server.hooks.v1.entrypoint;

import org.prebid.server.model.MultiMap;

public interface EntrypointPayload {

    MultiMap queryParams();

    MultiMap headers();

    String body();
}
