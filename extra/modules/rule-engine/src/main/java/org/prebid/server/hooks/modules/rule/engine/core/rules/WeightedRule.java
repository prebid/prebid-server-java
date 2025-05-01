package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.util.NoMatchingValueException;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.Objects;
import java.util.random.RandomGenerator;

public class WeightedRule<T> implements Rule<T> {

    private final RandomGenerator random;
    private final WeightedList<Rule<T>> weightedList;

    public WeightedRule(RandomGenerator random, WeightedList<Rule<T>> weightedList) {
        this.random = Objects.requireNonNull(random);
        this.weightedList = Objects.requireNonNull(weightedList);
    }

    @Override
    public RuleResult<T> process(T value) {
        try {
            return weightedList.getForSeed(random.nextDouble()).process(value);
        } catch (NoMatchingValueException e) {
            return RuleResult.unaltered(value);
        }
    }
}
