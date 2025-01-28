package org.prebid.server.execution.ruleengine.extractors;

public interface ArgumentExtractor<T, R> {

    R extract(T input);

    R extract(String input);
}
