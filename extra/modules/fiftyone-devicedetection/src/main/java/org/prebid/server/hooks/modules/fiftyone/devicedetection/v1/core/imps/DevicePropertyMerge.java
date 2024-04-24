package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Device.DeviceBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatch;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.SimplePropertyMerge;

import java.util.function.Function;
import java.util.function.Predicate;

public final class DevicePropertyMerge<T> implements DevicePatch, DevicePropertyMergeCondition {
    private final SimplePropertyMerge<DevicePropertySetterFactory<T>, DeviceInfo, T> baseMerge;

    public DevicePropertyMerge(
            DevicePropertySetterFactory<T> setterFactory,
            Function<DeviceInfo, T> getter,
            Predicate<T> isUsable)
    {
        this.baseMerge = new SimplePropertyMerge<>(setterFactory, getter, isUsable);
    }

    @Override
    public boolean patch(DeviceBuilder deviceBuilder, Device oldDevice, DeviceInfo newData) {
        final T value = baseMerge.getter().apply(newData);
        if (value == null || !baseMerge.isUsable().test(value)) {
            return false;
        }
        baseMerge.setter().makeFrom(oldDevice).set(deviceBuilder, value);
        return true;
    }

    @Override
    public boolean test(DeviceInfo deviceInfo) {
        final T value = baseMerge.getter().apply(deviceInfo);
        return (value == null || !baseMerge.isUsable().test(value));
    }
}
