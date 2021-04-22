package org.prebid.server.hooks.execution.v1.entrypoint;

import io.vertx.core.MultiMap;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class EntrypointPayloadImpl implements EntrypointPayload {

    MultiMap queryParams;

    MultiMap headers;

    String body;
}
