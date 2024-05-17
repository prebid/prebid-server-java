package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.WritableDeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.mergers.ValueSetter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DevicePatch;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.SimplePropertyMerge;

import java.util.function.Function;
import java.util.function.Predicate;

public final class DevicePropertyMerge<T> implements DevicePatch, DevicePropertyMergeCondition {
    private final SimplePropertyMerge<ValueSetter<WritableDeviceInfo, T>, DeviceInfo, T> baseMerge;

    public DevicePropertyMerge(
            ValueSetter<WritableDeviceInfo, T> setterFactory,
            Function<DeviceInfo, T> getter,
            Predicate<T> isUsable)
    {
        this.baseMerge = new SimplePropertyMerge<>(setterFactory, getter, isUsable);
    }

    @Override
    public boolean patch(WritableDeviceInfo writableDeviceInfo, DeviceInfo newData) {
        final T value = baseMerge.getter().apply(newData);
        if (value == null || !baseMerge.isUsable().test(value)) {
            return false;
        }
        baseMerge.setter().set(writableDeviceInfo, value);
        return true;
    }

    @Override
    public boolean test(DeviceInfo deviceInfo) {
        final T value = baseMerge.getter().apply(deviceInfo);
        return (value == null || !baseMerge.isUsable().test(value));
    }
}
