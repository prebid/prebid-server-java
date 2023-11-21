package org.prebid.server.spring.dynamic.properties;

import org.prebid.server.spring.dynamic.CompositePropertyUpdateListener;
import org.prebid.server.spring.dynamic.PropertyUpdateListener;

public class DynamicProperty<T> implements Property<T>, UpdatableProperty<T> {

    private volatile T value;
    private final CompositePropertyUpdateListener<T> listener = new CompositePropertyUpdateListener<>();

    public DynamicProperty(T initial) {
        value = initial;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    public void set(T newValue) {
        value = newValue;
        listener.onUpdate(newValue);
    }

    @Override
    public void addListener(PropertyUpdateListener<T> listener) {
        this.listener.addListener(listener);
    }
}
