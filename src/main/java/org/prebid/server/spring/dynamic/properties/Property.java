package org.prebid.server.spring.dynamic.properties;

import org.prebid.server.spring.dynamic.PropertyUpdateListener;

import java.util.function.Function;

public interface Property<T> {

    T get();

    void addListener(PropertyUpdateListener<T> listener);

    default <U> Property<U> wrap(Function<T, U> mapper) {
        final DynamicProperty<U> wrapper = new DynamicProperty<>(mapper.apply(get()));
        addListener(newValue -> wrapper.set(mapper.apply(newValue)));

        return wrapper;
    }
}
