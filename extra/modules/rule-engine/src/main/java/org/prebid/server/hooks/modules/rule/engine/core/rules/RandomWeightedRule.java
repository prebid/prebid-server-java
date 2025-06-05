package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.Objects;
import java.util.random.RandomGenerator;

public class RandomWeightedRule<T, C> implements Rule<T, C> {

    private final RandomGenerator random;
    private final WeightedList<Rule<T, C>> weightedList;

    public RandomWeightedRule(RandomGenerator random, WeightedList<Rule<T, C>> weightedList) {
        this.random = Objects.requireNonNull(random);
        this.weightedList = Objects.requireNonNull(weightedList);
    }

    @Override
    public RuleResult<T> process(T value, C context) {
        return weightedList.getForSeed(random.nextInt(weightedList.maxSeed() + 1)).process(value, context);
    }
}
