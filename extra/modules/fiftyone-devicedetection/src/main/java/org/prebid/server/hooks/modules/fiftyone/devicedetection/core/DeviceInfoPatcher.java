package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

/**
 * A function that applies a set of property overrides to
 * an instance of {@link DeviceInfoBox}
 *
 * @param <DeviceInfoBox> Type of writable object to accept overrides.
 */
@FunctionalInterface
public interface DeviceInfoPatcher<DeviceInfoBox> {
    DeviceInfoBox patchDeviceInfo(DeviceInfoBox rawDevice, DevicePatchPlan patchPlan, DeviceInfo newData);
}
