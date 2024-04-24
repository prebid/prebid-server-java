package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Device.DeviceBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers.ValueSetter;

import java.util.function.Function;

@FunctionalInterface
public interface DevicePropertySetterFactory<T> extends Function<Device, ValueSetter<DeviceBuilder, T>> {
    default ValueSetter<DeviceBuilder, T> makeFrom(Device oldDevice) {
        return apply(oldDevice);
    }
}
