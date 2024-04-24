package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.iab.openrtb.request.Device;

import java.util.function.Function;

@FunctionalInterface
public interface DevicePatchPlanner extends Function<Device, DevicePatchPlan> {
    default DevicePatchPlan buildPatchPlanFor(Device device) {
        return apply(device);
    }
}
