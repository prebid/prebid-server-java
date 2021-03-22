package org.prebid.server.hooks.execution;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.endpoint.EndpointPayload;
import org.prebid.server.model.Endpoint;

public class HookStageExecutor {

    public Future<HookStageExecutionResult<EndpointPayload>> executeEndpointHooks(
            MultiMap queryParams,
            MultiMap headers,
            String body,
            Endpoint endpoint) {

        return null;
    }
}
