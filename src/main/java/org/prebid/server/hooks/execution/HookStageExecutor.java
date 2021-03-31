package org.prebid.server.hooks.execution;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;

public class HookStageExecutor {

    public Future<HookStageExecutionResult<EntrypointPayload>> executeEntrypointStage(
            MultiMap queryParams,
            MultiMap headers,
            String body,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, new EntrypointPayload() {
            @Override
            public MultiMap queryParams() {
                return queryParams;
            }

            @Override
            public MultiMap headers() {
                return headers;
            }

            @Override
            public String body() {
                return body;
            }
        }));
    }
}
