package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DevicePatch;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfoPatcher;

import java.util.Map;
import java.util.function.Function;

public final class DeviceInfoPatcherImp<DeviceInfoBox, DeviceInfoBoxBuilder> implements DeviceInfoPatcher<DeviceInfoBox>
{
    private final Function<DeviceInfoBox, DeviceInfoBuilderAdapter<DeviceInfoBox, DeviceInfoBoxBuilder>> adapterFactory;

    public DeviceInfoPatcherImp(Function<DeviceInfoBox,
            DeviceInfoBuilderAdapter<DeviceInfoBox, DeviceInfoBoxBuilder>> adapterFactory) {
        this.adapterFactory = adapterFactory;
    }

    public DeviceInfoBox patchDeviceInfo(
            DeviceInfoBox rawDevice,
            DevicePatchPlan patchPlan,
            DeviceInfo newData)
    {
        DeviceInfoBuilderAdapter<DeviceInfoBox, DeviceInfoBoxBuilder> writableDevice = adapterFactory.apply(rawDevice);
        boolean didChange = false;
        for (Map.Entry<String, DevicePatch> namedPatch : patchPlan.patches()) {
            final boolean propChanged = namedPatch.getValue().patch(writableDevice, newData);
            if (propChanged) {
                didChange = true;
            }
        }
        return didChange ? writableDevice.rebuildBox() : rawDevice;
    }
}
