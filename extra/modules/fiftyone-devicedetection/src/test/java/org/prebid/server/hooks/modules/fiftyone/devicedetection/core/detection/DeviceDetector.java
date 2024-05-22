package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.WritableDeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiPredicate;

@FunctionalInterface
public interface DeviceDetector {
    boolean populateDeviceInfo(
            WritableDeviceInfo writableDeviceInfo,
            CollectedEvidence collectedEvidence,
            Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> devicePatchPlan,
            EnrichmentResult.EnrichmentResultBuilder<?> enrichmentResultBuilder);
}
