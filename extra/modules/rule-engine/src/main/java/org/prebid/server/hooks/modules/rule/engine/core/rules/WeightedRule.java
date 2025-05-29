package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.util.NoMatchingValueException;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.Objects;
import java.util.random.RandomGenerator;

public class WeightedRule<T, C> implements Rule<T, C> {

    private final RandomGenerator random;
    private final WeightedList<Rule<T, C>> weightedList;

    public WeightedRule(RandomGenerator random, WeightedList<Rule<T, C>> weightedList) {
        this.random = Objects.requireNonNull(random);
        this.weightedList = Objects.requireNonNull(weightedList);
    }

    @Override
    public RuleResult<T> process(T value, C context) {
        try {
            return weightedList.getForSeed(random.nextDouble()).process(value, context);
        } catch (NoMatchingValueException e) {
            return RuleResult.unaltered(value);
        }
    }
}
