package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class CompositeRule<T, C> implements Rule<T, C> {

    List<Rule<T, C>> subrules;

    @Override
    public RuleResult<T> process(T value, C context) {
        RuleResult<T> result = RuleResult.noAction(value);

        for (Rule<T, C> subrule : subrules) {
            result = result.mergeWith(subrule.process(value, context));

            if (result.isReject())
                return result;
        }

        return result;
    }
}
