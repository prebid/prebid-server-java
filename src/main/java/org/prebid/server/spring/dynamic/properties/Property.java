package org.prebid.server.spring.dynamic.properties;

import org.prebid.server.spring.dynamic.DynamicPropertyUpdateListener;

import java.util.function.Function;

public interface Property<T> {

    T get();

    void addListener(DynamicPropertyUpdateListener<T> listener);

    default <U> Property<U> wrap(Function<T, U> mapper) {
        final DynamicProperty<U> wrapper = new DynamicProperty<>(mapper.apply(get()));
        addListener(newValue -> {
            try {
                wrapper.set(mapper.apply(newValue));
            } catch (Throwable t) {
                // TODO: add log?
            }
        });

        return wrapper;
    }
}
