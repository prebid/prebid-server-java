package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class CompositeRule<T> implements Rule<T> {

    List<Rule<T>> subrules;

    @Override
    public RuleResult<T> process(T value) {
        return subrules.stream().reduce(
                RuleResult.unaltered(value),
                (result, rule) -> result.mergeWith(rule.process(result.getUpdateResult().getValue())),
                RuleResult::mergeWith);
    }
}
