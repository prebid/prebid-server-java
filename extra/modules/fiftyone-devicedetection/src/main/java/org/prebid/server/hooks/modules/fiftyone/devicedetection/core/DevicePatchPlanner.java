package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import java.util.function.Function;

/**
 * Checks which properties of {@link com.iab.openrtb.request.Device}
 * currently have no values and can be populated by this module.
 */
@FunctionalInterface
public interface DevicePatchPlanner extends Function<DeviceInfo, DevicePatchPlan> {
    /**
     * Alias for {@link #apply}
     *
     * @param deviceInfo A device that this module should attempt to enrich.
     * @return A collection of transfer functions to copy properties from estimation object.
     */
    default DevicePatchPlan buildPatchPlanFor(DeviceInfo deviceInfo) {
        return apply(deviceInfo);
    }
}
