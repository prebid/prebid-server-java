package org.prebid.server.hooks.execution.v1.entrypoint;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.MultiMap;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class EntrypointPayloadImpl implements EntrypointPayload {

    MultiMap queryParams;

    MultiMap headers;

    String body;
}
