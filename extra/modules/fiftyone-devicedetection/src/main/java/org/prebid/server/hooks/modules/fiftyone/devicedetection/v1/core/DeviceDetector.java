package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;

import java.util.function.BiFunction;

@FunctionalInterface
public interface DeviceDetector extends BiFunction<CollectedEvidence, DevicePatchPlan, DeviceInfo> {
    default DeviceInfo inferProperties(CollectedEvidence collectedEvidence, DevicePatchPlan devicePatchPlan) {
        return apply(collectedEvidence, devicePatchPlan);
    }
}
