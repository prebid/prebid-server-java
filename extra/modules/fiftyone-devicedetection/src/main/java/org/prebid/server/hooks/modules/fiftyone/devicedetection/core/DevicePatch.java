package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

/**
 * Attempts to get a single property from {@link DeviceInfo}
 * and assign it to the respective property in {@link WritableDeviceInfo}.
 */
@FunctionalInterface
public interface DevicePatch {
    /**
     * @param writableDeviceInfo Writable object to set the property in.
     * @param newData Readable object to get the property from.
     * @return Whether the value was successfully updated in writable object.
     */
    boolean patch(WritableDeviceInfo writableDeviceInfo, DeviceInfo newData);
}
