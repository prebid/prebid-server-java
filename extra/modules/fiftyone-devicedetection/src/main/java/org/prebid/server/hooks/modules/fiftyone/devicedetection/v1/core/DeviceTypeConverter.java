package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import java.util.function.Function;

@FunctionalInterface
public interface DeviceTypeConverter extends Function<String, Integer> {
}
