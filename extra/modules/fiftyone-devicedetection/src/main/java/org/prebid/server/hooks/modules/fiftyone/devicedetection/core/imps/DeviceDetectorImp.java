package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.DeviceInfoClone;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceDetector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfoPatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceTypeConverter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.PipelineSupplier;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.PriorityEvidenceSelector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo;

public final class DeviceDetectorImp implements DeviceDetector {
    private final PipelineSupplier pipelineSupplier;
    private final PriorityEvidenceSelector priorityEvidenceSelector;
    private final DeviceTypeConverter deviceTypeConverter;
    private final DeviceInfoPatcher<DeviceInfoClone> deviceInfoPatcher;

    public DeviceDetectorImp(
            PipelineSupplier pipelineSupplier,
            PriorityEvidenceSelector priorityEvidenceSelector,
            DeviceTypeConverter deviceTypeConverter,
            DeviceInfoPatcher<DeviceInfoClone> deviceInfoPatcher)
    {
        this.pipelineSupplier = pipelineSupplier;
        this.priorityEvidenceSelector = priorityEvidenceSelector;
        this.deviceTypeConverter = deviceTypeConverter;
        this.deviceInfoPatcher = deviceInfoPatcher;
    }

    @Override
    public DeviceInfo apply(CollectedEvidence collectedEvidence, DevicePatchPlan patchPlan) {
        try (FlowData data = pipelineSupplier.get().createFlowData()) {
            data.addEvidence(priorityEvidenceSelector.pickRelevantFrom(collectedEvidence));
            data.process();
            DeviceData device = data.get(DeviceData.class);
            if (device == null) {
                return null;
            }
            return deviceInfoPatcher.patchDeviceInfo(
                    DeviceInfoClone.builder().build(),
                    patchPlan,
                    new DeviceDataWrapper(device, deviceTypeConverter)
            );
        } catch (Exception e) {
            // will be caught by `GroupResult.applyPayloadUpdate`
            throw new RuntimeException(e);
        }
    }
}
