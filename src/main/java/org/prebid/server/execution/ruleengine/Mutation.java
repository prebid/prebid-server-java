package org.prebid.server.execution.ruleengine;

public interface Mutation<T> {

    T mutate(T value);
}
