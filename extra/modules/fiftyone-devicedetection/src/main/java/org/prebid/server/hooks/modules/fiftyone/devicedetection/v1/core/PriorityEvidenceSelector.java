package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;

import java.util.Map;
import java.util.function.Function;

@FunctionalInterface
public interface PriorityEvidenceSelector extends Function<CollectedEvidence, Map<String, String>> {
    default Map<String, String> pickRelevantFrom(CollectedEvidence collectedEvidence) {
        return apply(collectedEvidence);
    }
}
