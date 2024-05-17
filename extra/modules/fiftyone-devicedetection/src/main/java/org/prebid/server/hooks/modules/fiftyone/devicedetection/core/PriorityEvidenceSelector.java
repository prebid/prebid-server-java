package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;

import java.util.Map;
import java.util.function.Function;

/**
 * Given {@link CollectedEvidence} about device composed through multiple stages
 * (may include raw, parsed and reconstructed headers etc.)
 * selects a subset of it to be used in estimation/inference of device properties.
 */
@FunctionalInterface
public interface PriorityEvidenceSelector extends Function<CollectedEvidence, Map<String, String>> {
    /**
     * Alias for {@link #apply}
     *
     * @param collectedEvidence All evidence about device
     *                          collected at different stages.
     *                          May include raw, parsed and reconstructed headers etc.
     * @return Selected subset of evidence to be used in estimation/inference of device properties.
     */
    default Map<String, String> pickRelevantFrom(CollectedEvidence collectedEvidence) {
        return apply(collectedEvidence);
    }
}
