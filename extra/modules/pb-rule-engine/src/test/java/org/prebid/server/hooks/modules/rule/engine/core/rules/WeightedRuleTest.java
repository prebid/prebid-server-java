package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class WeightedRuleTest {

    @Test
    public void processShouldUtilizeRuleFromWeightedList() {
        // given
        final WeightedList<Rule<Object, Object>> ruleList =
                (WeightedList<Rule<Object, Object>>) mock(WeightedList.class);
        final RuleResult<Object> stub = RuleResult.unaltered(new Object());
        given(ruleList.getForSeed(anyInt())).willReturn((left, right) -> stub);

        final RandomGenerator randomGenerator = mock(RandomGenerator.class);
        given(randomGenerator.nextDouble()).willReturn(0.5);

        final RandomWeightedRule<Object, Object> rule = RandomWeightedRule.of(randomGenerator, ruleList);

        // when
        final RuleResult<Object> result = rule.process(new Object(), new Object());

        // then
        assertThat(result).isEqualTo(stub);
    }
}
