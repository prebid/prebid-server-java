package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class CompositeRule<T, C> implements Rule<T, C> {

    List<Rule<T, C>> subrules;

    @Override
    public RuleResult<T> process(T value, C context) {
        return subrules.stream().reduce(
                RuleResult.unaltered(value),
                (result, rule) -> result.mergeWith(rule.process(result.getUpdateResult().getValue(), context)),
                RuleResult::mergeWith);
    }
}
