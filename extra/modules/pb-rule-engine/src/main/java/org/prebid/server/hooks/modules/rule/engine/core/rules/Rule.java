package org.prebid.server.hooks.modules.rule.engine.core.rules;

public interface Rule<T, C> {

    RuleResult<T> process(T value, C context);
}
