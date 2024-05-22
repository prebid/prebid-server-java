package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence.CollectedEvidenceBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
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
            InvocationContext invocationContext)
    {
        final CollectedEvidenceBuilder evidenceBuilder = CollectedEvidence.builder();
        collectEvidence(evidenceBuilder, payload);

        return Future.succeededFuture(
                InvocationResultImpl.<EntrypointPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(
                                ModuleContext
                                        .builder()
                                        .collectedEvidence(evidenceBuilder.build())
                                        .build())
                        .build());
    }

    protected void collectEvidence(CollectedEvidenceBuilder evidenceBuilder, EntrypointPayload entrypointPayload) {
        evidenceBuilder.rawHeaders(entrypointPayload.headers().entries());
    }
}
