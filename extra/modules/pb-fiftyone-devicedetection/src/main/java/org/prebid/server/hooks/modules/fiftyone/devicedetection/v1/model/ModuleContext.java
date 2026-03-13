package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model;

import lombok.Builder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;

@Builder(toBuilder = true)
public record ModuleContext(CollectedEvidence collectedEvidence) {
}
