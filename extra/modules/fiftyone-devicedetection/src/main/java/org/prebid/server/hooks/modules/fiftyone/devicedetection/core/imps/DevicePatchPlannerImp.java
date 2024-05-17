package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.WritableDeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DevicePatch;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DevicePatchPlanner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public final class DevicePatchPlannerImp implements DevicePatchPlanner {
    private static final Map<String, DevicePropertyMerge<?>> propertiesToMerge = Map.ofEntries(
            entry("DeviceType", new DevicePropertyMerge<>(WritableDeviceInfo::setDeviceType, DeviceInfo::getDeviceType, v -> v > 0)),
            entry("Make", new DevicePropertyMerge<>(WritableDeviceInfo::setMake, DeviceInfo::getMake, s -> !s.isEmpty())),
            entry("Model", new DevicePropertyMerge<>(WritableDeviceInfo::setModel, DeviceInfo::getModel, s -> !s.isEmpty())),
            entry("Os", new DevicePropertyMerge<>(WritableDeviceInfo::setOs, DeviceInfo::getOs, s -> !s.isEmpty())),
            entry("Osv", new DevicePropertyMerge<>(WritableDeviceInfo::setOsv, DeviceInfo::getOsv, s -> !s.isEmpty())),
            entry("H", new DevicePropertyMerge<>(WritableDeviceInfo::setH, DeviceInfo::getH, v -> v > 0)),
            entry("W", new DevicePropertyMerge<>(WritableDeviceInfo::setW, DeviceInfo::getW, v -> v > 0)),
            entry("Ppi", new DevicePropertyMerge<>(WritableDeviceInfo::setPpi, DeviceInfo::getPpi, v -> v > 0)),
            entry("PixelRatio", new DevicePropertyMerge<>(WritableDeviceInfo::setPixelRatio, DeviceInfo::getPixelRatio, (BigDecimal v) -> v.intValue() > 0)),
            entry("DeviceID", new DevicePropertyMerge<>(WritableDeviceInfo::setDeviceId, DeviceInfo::getDeviceId, s -> !s.isEmpty()))
    );

    @Override
    public DevicePatchPlan apply(DeviceInfo deviceInfo) {
        return new DevicePatchPlan(propertiesToMerge.entrySet().stream()
                .filter(nextMerge -> nextMerge.getValue().shouldReplacePropertyIn(deviceInfo))
                .map(e -> new AbstractMap.SimpleEntry<String, DevicePatch>(e.getKey(), e.getValue()))
                .collect(Collectors.toUnmodifiableList())
        );
    }
}
