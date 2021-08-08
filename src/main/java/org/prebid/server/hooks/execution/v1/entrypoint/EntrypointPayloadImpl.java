package org.prebid.server.hooks.execution.v1.entrypoint;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class EntrypointPayloadImpl implements EntrypointPayload {

    CaseInsensitiveMultiMap queryParams;

    CaseInsensitiveMultiMap headers;

    String body;
}
