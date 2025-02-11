package org.prebid.server.hooks.modules.rule.engine.core.rules;

public interface ArgumentExtractor<T> {

    String extract(T value);
}
