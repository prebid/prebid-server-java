package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;

public class AlternativeActionRule<T, C> implements Rule<T, C> {

    private final Rule<T, C> delegate;
    private final Rule<T, C> alternative;

    public AlternativeActionRule(Rule<T, C> delegate, Rule<T, C> alternative) {
        this.delegate = delegate;
        this.alternative = alternative;
    }

    public RuleResult<T> process(T value, C context) {
        try {
            return delegate.process(value, context);
        } catch (NoMatchingRuleException e) {
            return alternative.process(value, context);
        }
    }
}
