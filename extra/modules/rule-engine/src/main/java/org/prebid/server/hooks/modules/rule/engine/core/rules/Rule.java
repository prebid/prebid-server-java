package org.prebid.server.hooks.modules.rule.engine.core.rules;

public interface Rule<T> {

    RuleResult<T> process(T value);
}
