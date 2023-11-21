package org.prebid.server.spring.dynamic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CompositeDynamicPropertyUpdateListener<T> implements DynamicPropertyUpdateListener<T> {

    private final List<DynamicPropertyUpdateListener<T>> delegates = new CopyOnWriteArrayList<>();

    public void addListener(DynamicPropertyUpdateListener<T> listener) {
        delegates.add(listener);
    }

    @Override
    public void onUpdate(T newValue) {
        delegates.forEach(delegate -> delegate.onUpdate(newValue));
    }
}
