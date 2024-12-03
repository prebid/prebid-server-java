package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import io.vertx.core.Future;

public class FiftyOneDeviceDetectionEntrypointHook implements EntrypointHook {
    private static final String CODE = "fiftyone-devicedetection-entrypoint-hook";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(
            EntrypointPayload payload,
            InvocationContext invocationContext) {
        return Future.succeededFuture(
                InvocationResultImpl.<EntrypointPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(
                                ModuleContext
                                        .builder()
                                        .collectedEvidence(
                                                CollectedEvidence
                                                        .builder()
                                                        .rawHeaders(payload.headers().entries())
                                                        .build()
                                        )
                                        .build())
                        .build());
    }
}
