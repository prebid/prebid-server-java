package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context;

import lombok.Builder;

@Builder(toBuilder = true)
public record ModuleContext(CollectedEvidence collectedEvidence) {
}
