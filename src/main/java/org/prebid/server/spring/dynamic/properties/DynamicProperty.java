package org.prebid.server.spring.dynamic.properties;

import org.prebid.server.spring.dynamic.CompositePropertyUpdateListener;
import org.prebid.server.spring.dynamic.PropertyType;
import org.prebid.server.spring.dynamic.PropertyUpdateListener;

import java.util.Objects;

public class DynamicProperty<T> implements Property<T>, UpdatableProperty<T> {

    private volatile T value;
    private final PropertyType type;

    private final CompositePropertyUpdateListener<T> listener = new CompositePropertyUpdateListener<>();

    public DynamicProperty(T initial) {
        value = Objects.requireNonNull(initial);
        this.type = PropertyType.from(initial.getClass());
    }

    @Override
    public PropertyType getType() {
        return type;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    public void set(T newValue) {
        value = Objects.requireNonNull(newValue);
        listener.onUpdate(newValue);
    }

    @Override
    public void addListener(PropertyUpdateListener<T> listener) {
        this.listener.addListener(listener);
    }
}
