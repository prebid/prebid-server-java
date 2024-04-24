package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface ValueSetter<Target, Value> extends BiConsumer<Target, Value> {
    default void set(Target target, Value value) {
        accept(target, value);
    }
}
