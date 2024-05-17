package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo;

import java.util.function.Predicate;

@FunctionalInterface
public interface DevicePropertyMergeCondition extends Predicate<DeviceInfo> {
    default boolean shouldReplacePropertyIn(DeviceInfo currentDevice) {
        return test(currentDevice);
    }
}
