package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Device.DeviceBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatch;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlanner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public final class DevicePatchPlannerImp implements DevicePatchPlanner {
    private static final Map<String, DevicePropertyMerge<?>> propertiesToMerge = Map.ofEntries(
            entry("Devicetype", new DevicePropertyMerge<>(d -> DeviceBuilder::devicetype, DeviceInfo::getDeviceType, v -> v > 0)),
            entry("Make", new DevicePropertyMerge<>(d -> DeviceBuilder::make, DeviceInfo::getMake, s -> !s.isEmpty())),
            entry("Model", new DevicePropertyMerge<>(d -> DeviceBuilder::model, DeviceInfo::getModel, s -> !s.isEmpty())),
            entry("Os", new DevicePropertyMerge<>(d -> DeviceBuilder::os, DeviceInfo::getOs, s -> !s.isEmpty())),
            entry("Osv", new DevicePropertyMerge<>(d -> DeviceBuilder::osv, DeviceInfo::getOsv, s -> !s.isEmpty())),
            entry("H", new DevicePropertyMerge<>(d -> DeviceBuilder::h, DeviceInfo::getH, v -> v > 0)),
            entry("W", new DevicePropertyMerge<>(d -> DeviceBuilder::w, DeviceInfo::getW, v -> v > 0)),
            entry("Ppi", new DevicePropertyMerge<>(d -> DeviceBuilder::ppi, DeviceInfo::getPpi, v -> v > 0)),
            entry("Pxratio", new DevicePropertyMerge<>(d -> DeviceBuilder::pxratio, DeviceInfo::getPixelRatio, (BigDecimal v) -> v.intValue() > 0)),
            entry("DeviceID", new DevicePropertyMerge<>(
                    device -> (builder, value) -> DeviceMirror.setDeviceId(builder, device, value),
                    DeviceInfo::getDeviceId,
                    s -> !s.isEmpty()))
    );

    @Override
    public DevicePatchPlan apply(Device device) {
        final DeviceInfo deviceAsInfoSource = new DeviceMirror(device);
        return new DevicePatchPlan(propertiesToMerge.entrySet().stream()
                .filter(nextMerge -> nextMerge.getValue().shouldReplacePropertyIn(deviceAsInfoSource))
                .map(e -> new AbstractMap.SimpleEntry<String, DevicePatch>(e.getKey(), e.getValue()))
                .collect(Collectors.toUnmodifiableList())
        );
    }
}
