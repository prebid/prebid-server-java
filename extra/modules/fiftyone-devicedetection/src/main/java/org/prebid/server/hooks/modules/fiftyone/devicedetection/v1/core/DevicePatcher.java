package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.iab.openrtb.request.Device;

@FunctionalInterface
public interface DevicePatcher {
    Device patchDevice(Device rawDevice, DevicePatchPlan patchPlan, DeviceInfo newData);
}
