package org.prebid.server.spring.dynamic;

public interface PropertyUpdateListener<T> {

    void onUpdate(T newValue);
}
