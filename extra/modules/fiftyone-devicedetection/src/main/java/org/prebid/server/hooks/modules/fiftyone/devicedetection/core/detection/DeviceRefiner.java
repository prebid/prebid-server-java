package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters.DeviceInfoBuilderMethodSet;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;

public interface DeviceRefiner {
    <DeviceInfoBox, DeviceInfoBoxBuilder> EnrichmentResult<DeviceInfoBox> enrichDeviceInfo(
            DeviceInfo rawDeviceInfo,
            CollectedEvidence collectedEvidence,
            DeviceInfoBuilderMethodSet<DeviceInfoBox, DeviceInfoBoxBuilder>.Adapter writableAdapter);
}
