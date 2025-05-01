package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;

public class AlternativeActionRule<T> implements Rule<T> {

    private final Rule<T> delegate;
    private final Rule<T> alternative;

    public AlternativeActionRule(Rule<T> delegate, Rule<T> alternative) {
        this.delegate = delegate;
        this.alternative = alternative;
    }

    public RuleResult<T> process(T value) {
        try {
            return delegate.process(value);
        } catch (NoMatchingRuleException e) {
            return alternative.process(value);
        }
    }
}
