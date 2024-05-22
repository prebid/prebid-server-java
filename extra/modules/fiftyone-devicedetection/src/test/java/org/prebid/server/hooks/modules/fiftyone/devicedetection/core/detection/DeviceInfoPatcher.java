package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.WritableDeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiPredicate;

@FunctionalInterface
public interface DeviceInfoPatcher {
    boolean patchDeviceInfo(
            WritableDeviceInfo writableDeviceInfo,
            Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan,
            DeviceInfo newData,
            EnrichmentResult.EnrichmentResultBuilder<?> enrichmentResultBuilder);
}
