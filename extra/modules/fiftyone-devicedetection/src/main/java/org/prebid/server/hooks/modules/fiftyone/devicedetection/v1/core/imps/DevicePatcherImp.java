package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatch;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatcher;

import java.util.Map;

public final class DevicePatcherImp implements DevicePatcher {
    public Device patchDevice(
            Device rawDevice,
            DevicePatchPlan patchPlan,
            DeviceInfo newData)
    {
        Device.DeviceBuilder newDeviceBuilder = rawDevice.toBuilder();
        boolean didChange = false;
        for (Map.Entry<String, DevicePatch> namedPatch : patchPlan.patches()) {
            final boolean propChanged = namedPatch.getValue().patch(newDeviceBuilder, rawDevice, newData);
            if (propChanged) {
                didChange = true;
            }
        }
        return didChange ? newDeviceBuilder.build() : rawDevice;
    }
}
