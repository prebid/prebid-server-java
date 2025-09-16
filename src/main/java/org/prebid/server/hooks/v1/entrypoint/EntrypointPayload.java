package org.prebid.server.hooks.v1.entrypoint;

import org.prebid.server.model.CaseInsensitiveMultiMap;

public interface EntrypointPayload {

    CaseInsensitiveMultiMap queryParams();

    CaseInsensitiveMultiMap headers();

    String body();
}
