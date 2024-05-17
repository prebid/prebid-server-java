package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;

import java.util.function.BiFunction;

/**
 * A transformer that given a set of evidence (e.g. HTTP headers)
 * and a set of expected properties
 * produces a new object with those properties estimated based on provided evidence.
 */
@FunctionalInterface
public interface DeviceDetector extends BiFunction<CollectedEvidence, DevicePatchPlan, DeviceInfo> {
    /**
     * Alias of {@link #apply}.
     *
     * @param collectedEvidence A set of available information on device (HTTP headers etc.)
     * @param devicePatchPlan A set of property descriptors/transfer methods
     * @return A representation of device populated with expected properties by estimation/inference
     */
    default DeviceInfo inferProperties(CollectedEvidence collectedEvidence, DevicePatchPlan devicePatchPlan) {
        return apply(collectedEvidence, devicePatchPlan);
    }
}
