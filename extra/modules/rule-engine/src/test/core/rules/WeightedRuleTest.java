package core.rules;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.WeightedRule;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class WeightedRuleTest {

    @Test
    public void processShouldUtilizeRuleFromWeightedList() {
        // given
        final WeightedList<Rule<Object>> ruleList = (WeightedList<Rule<Object>>) mock(WeightedList.class);
        final RuleResult<Object> stub = RuleResult.unaltered(new Object());
        given(ruleList.getForSeed(anyDouble())).willReturn(value -> stub);

        final RandomGenerator randomGenerator = mock(RandomGenerator.class);
        given(randomGenerator.nextDouble()).willReturn(0.5);

        final WeightedRule<Object> rule = new WeightedRule<>(randomGenerator, ruleList);

        // when
        final RuleResult<Object> result = rule.process(rule);

        // then
        assertThat(result).isEqualTo(stub);
    }
}
