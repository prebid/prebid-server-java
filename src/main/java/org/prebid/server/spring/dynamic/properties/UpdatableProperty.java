package org.prebid.server.spring.dynamic.properties;

public interface UpdatableProperty<T> {

    void set(T newValue);
}
