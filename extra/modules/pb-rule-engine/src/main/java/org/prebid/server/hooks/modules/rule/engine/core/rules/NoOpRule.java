package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;

@Value(staticConstructor = "create")
public class NoOpRule<T, C> implements Rule<T, C> {

    @Override
    public RuleResult<T> process(T value, C context) {
        return RuleResult.unaltered(value);
    }
}
