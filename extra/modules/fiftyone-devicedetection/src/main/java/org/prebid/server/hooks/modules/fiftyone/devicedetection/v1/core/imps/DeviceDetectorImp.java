package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceDetector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceTypeConverter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineSupplier;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PriorityEvidenceSelector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;

public final class DeviceDetectorImp implements DeviceDetector {
    private final PipelineSupplier pipelineSupplier;
    private final PriorityEvidenceSelector priorityEvidenceSelector;
    private final DeviceTypeConverter deviceTypeConverter;
    private final DevicePatcher devicePatcher;

    public DeviceDetectorImp(
            PipelineSupplier pipelineSupplier,
            PriorityEvidenceSelector priorityEvidenceSelector,
            DeviceTypeConverter deviceTypeConverter,
            DevicePatcher devicePatcher)
    {
        this.pipelineSupplier = pipelineSupplier;
        this.priorityEvidenceSelector = priorityEvidenceSelector;
        this.deviceTypeConverter = deviceTypeConverter;
        this.devicePatcher = devicePatcher;
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
            final Device newDevice = devicePatcher.patchDevice(
                    Device.builder().build(),
                    patchPlan,
                    new DeviceDataWrapper(device, deviceTypeConverter)
            );
            return new DeviceMirror(newDevice);
        } catch (Exception e) {
            // will be caught by `GroupResult.applyPayloadUpdate`
            throw new RuntimeException(e);
        }
    }
}
