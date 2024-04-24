package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.BidRequestEvidenceCollector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.BidRequestPatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceDetector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlanner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence.CollectedEvidenceBuilder;

public final class BidRequestPatcherImp implements BidRequestPatcher {
    private final DevicePatchPlanner devicePatchPlanner;
    private final BidRequestEvidenceCollector bidRequestEvidenceCollector;
    private final DeviceDetector deviceDetector;
    private final DevicePatcher devicePatcher;

    public BidRequestPatcherImp(
            DevicePatchPlanner devicePatchPlanner,
            BidRequestEvidenceCollector bidRequestEvidenceCollector,
            DeviceDetector deviceDetector,
            DevicePatcher devicePatcher)
    {
        this.devicePatchPlanner = devicePatchPlanner;
        this.bidRequestEvidenceCollector = bidRequestEvidenceCollector;
        this.deviceDetector = deviceDetector;
        this.devicePatcher = devicePatcher;
    }

    @Override
    public BidRequest apply(BidRequest bidRequest, CollectedEvidence collectedEvidence) {
        if (bidRequest == null) {
            return null;
        }
        final Device existingDevice = bidRequest.getDevice();
        final DevicePatchPlan patchPlan = devicePatchPlanner.buildPatchPlanFor(existingDevice);

        if (patchPlan == null || patchPlan.isEmpty()) {
            return null;
        }

        final CollectedEvidenceBuilder evidenceBuilder = collectedEvidence.toBuilder();
        bidRequestEvidenceCollector.evidenceFrom(bidRequest).injectInto(evidenceBuilder);
        final DeviceInfo detectedDevice = deviceDetector.inferProperties(evidenceBuilder.build(), patchPlan);
        if (detectedDevice == null) {
            return null;
        }

        Device mergedDevice = devicePatcher.patchDevice(existingDevice, patchPlan, detectedDevice);
        if (mergedDevice == null || mergedDevice == existingDevice) {
            return null;
        }

        return bidRequest.toBuilder()
                .device(mergedDevice)
                .build();
    }
}
