package org.prebid.server.spring.dynamic.properties;

import org.prebid.server.spring.dynamic.CompositeDynamicPropertyUpdateListener;
import org.prebid.server.spring.dynamic.DynamicPropertyUpdateListener;

public class DynamicProperty<T> implements Property<T>, UpdatableProperty<T> {

    private volatile T value;
    private final CompositeDynamicPropertyUpdateListener<T> listener = new CompositeDynamicPropertyUpdateListener<>();

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
    public void addListener(DynamicPropertyUpdateListener<T> listener) {
        this.listener.addListener(listener);
    }
}
