package org.prebid.server.hooks.execution.v1.exitpoint;

import io.vertx.core.MultiMap;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.exitpoint.ExitpointPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class ExitpointPayloadImpl implements ExitpointPayload {

    MultiMap responseHeaders;

    String responseBody;
}
