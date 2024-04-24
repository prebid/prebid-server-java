package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.evidencecollectors;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EntrypointEvidenceCollector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence.CollectedEvidenceBuilder;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;

public final class EntrypointDataReader implements EntrypointEvidenceCollector {

    @Override
    public void accept(CollectedEvidenceBuilder evidenceBuilder, EntrypointPayload entrypointPayload) {
        evidenceBuilder.rawHeaders(entrypointPayload.headers().entries());
    }
}
