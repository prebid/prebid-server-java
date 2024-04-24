package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;

@FunctionalInterface
public interface EntrypointEvidenceCollector extends EvidenceCollector<EntrypointPayload> {
}
