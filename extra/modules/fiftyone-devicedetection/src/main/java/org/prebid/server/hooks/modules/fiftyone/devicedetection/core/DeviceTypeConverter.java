package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import java.util.function.Function;

/**
 * A function that converts {@link String} values from
 * {@link fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceType()}
 * to an {@link Integer} compatible with
 * {@link com.iab.openrtb.request.Device.DeviceBuilder#devicetype(Integer)}
 */
@FunctionalInterface
public interface DeviceTypeConverter extends Function<String, Integer> {
}
