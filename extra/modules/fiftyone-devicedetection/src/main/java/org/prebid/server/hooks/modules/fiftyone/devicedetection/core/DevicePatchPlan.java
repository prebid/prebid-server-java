package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import java.util.Collection;
import java.util.Map;

/**
 * A representation of which properties
 * of {@link com.iab.openrtb.request.Device}
 * this module will attempt to populate
 * to enrich {@link com.iab.openrtb.request.BidRequest}.
 *
 * @param patches A collection of named (by keys) value transfer functions
 *                that can copy a single property each
 *                from a {@link DeviceInfo}-compatible adapter around temporary data object
 *                to a {@link WritableDeviceInfo}-compatible destination.
 */
public record DevicePatchPlan(Collection<Map.Entry<String, DevicePatch>> patches) {
    /**
     * @return Whether there are properties that should be populated
     * in the original {@link com.iab.openrtb.request.Device}
     */
    public boolean isEmpty() {
        return patches == null || patches.isEmpty();
    }
}
