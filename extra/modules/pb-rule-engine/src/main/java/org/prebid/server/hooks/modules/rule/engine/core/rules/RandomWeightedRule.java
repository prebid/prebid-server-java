package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.random.RandomGenerator;

@Value(staticConstructor = "of")
public class RandomWeightedRule<T, C> implements Rule<T, C> {

    RandomGenerator random;
    WeightedList<Rule<T, C>> weightedList;

    @Override
    public RuleResult<T> process(T value, C context) {
        return weightedList.getForSeed(random.nextInt(weightedList.maxSeed() + 1)).process(value, context);
    }
}
