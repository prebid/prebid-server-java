package org.prebid.server.spring.dynamic;

public interface DynamicPropertyUpdateListener<T> {

    void onUpdate(T newValue);
}
