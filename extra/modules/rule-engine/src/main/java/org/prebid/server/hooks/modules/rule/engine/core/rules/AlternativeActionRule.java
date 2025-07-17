package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;

@Value(staticConstructor = "of")
public class AlternativeActionRule<T, C> implements Rule<T, C> {

    Rule<T, C> delegate;
    Rule<T, C> alternative;

    public RuleResult<T> process(T value, C context) {
        try {
            return delegate.process(value, context);
        } catch (NoMatchingRuleException e) {
            return alternative.process(value, context);
        }
    }
}
