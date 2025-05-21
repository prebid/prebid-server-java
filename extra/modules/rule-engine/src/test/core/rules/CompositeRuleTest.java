package core.rules;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.CompositeRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CompositeRuleTest {

    private static final Object VALUE = new Object();

    @Test
    public void processShouldAccumulateResultFromAllSubrules() {
        // given
        final Rule<Object> firstRule = (Rule<Object>) mock(Rule.class);
        given(firstRule.process(any())).willAnswer(invocationOnMock -> RuleResult.of(
                UpdateResult.updated(invocationOnMock.getArgument(0)),
                TagsImpl.of(singletonList(ActivityImpl.of("firstActivity", "success", emptyList())))));

        final Rule<Object> secondRule = (Rule<Object>) mock(Rule.class);
        given(secondRule.process(any())).willAnswer(invocationOnMock -> RuleResult.of(
                UpdateResult.updated(invocationOnMock.getArgument(0)),
                TagsImpl.of(singletonList(ActivityImpl.of("secondActivity", "success", emptyList())))));

        final CompositeRule<Object> target = CompositeRule.of(asList(firstRule, secondRule));

        // when
        final RuleResult<Object> result = target.process(VALUE);

        // then
        final Tags expectedTags = TagsImpl.of(
                asList(ActivityImpl.of("firstActivity", "success", emptyList()),
                        ActivityImpl.of("secondActivity", "success", emptyList())));

        assertThat(result).isEqualTo(RuleResult.of(UpdateResult.updated(VALUE), expectedTags));

        verify(firstRule).process(VALUE);
        verify(secondRule).process(VALUE);
    }
}
