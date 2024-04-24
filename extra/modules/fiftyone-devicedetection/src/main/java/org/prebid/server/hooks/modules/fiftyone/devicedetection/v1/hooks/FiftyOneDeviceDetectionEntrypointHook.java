package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EntrypointEvidenceCollector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.ModuleContextPatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.ModuleContext;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.result.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import io.vertx.core.Future;

public record FiftyOneDeviceDetectionEntrypointHook(
        EntrypointEvidenceCollector entrypointEvidenceCollector,
        ModuleContextPatcher moduleContextPatcher
) implements EntrypointHook
{
    private static final String CODE = "fiftyone-devicedetection-entrypoint-hook";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(
            EntrypointPayload payload,
            InvocationContext invocationContext)
    {
        final ModuleContext moduleContext = moduleContextPatcher.contextWithNewEvidence(
                null,
                entrypointEvidenceCollector.evidenceFrom(payload)
        );

        return Future.succeededFuture(
                InvocationResultImpl.<EntrypointPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .build());
    }
}
