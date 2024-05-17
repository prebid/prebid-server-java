package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.mergers;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.SimplePropertyMerge;

import java.util.function.Function;
import java.util.function.Predicate;

public final class PropertyMergeImp<Target, Source, Value> implements PropertyMerge<Target, Source> {
    private final SimplePropertyMerge<ValueSetter<Target, Value>, Source, Value> baseMerge;

    public PropertyMergeImp(
            Function<Source, Value> getter,
            Predicate<Value> isUsable,
            ValueSetter<Target, Value> setter)
    {
        this.baseMerge = new SimplePropertyMerge<>(setter, getter, isUsable);
    }

    @Override
    public void accept(Target target, Source propertySource) {
        final Value value = baseMerge.getter().apply(propertySource);
        if (value == null || !baseMerge.isUsable().test(value)) {
            return;
        }
        baseMerge.setter().set(target, value);
    }
}
