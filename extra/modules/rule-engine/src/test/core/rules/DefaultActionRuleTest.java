package core.rules;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.DefaultActionRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DefaultActionRuleTest {

    private static final Object VALUE = new Object();

    @Test
    public void processShouldAccumulateResultFromAllRuleActions() {
        // given
        final RuleAction<Object> firstAction = (RuleAction<Object>) mock(RuleAction.class);
        final ResultFunction<Object> firstFunction = (ResultFunction<Object>) mock(ResultFunction.class);

        given(firstFunction.apply(any())).willAnswer(prepareResultFunctionAnswer("firstAction"));
        given(firstAction.getFunction()).willReturn(firstFunction);
        given(firstAction.getConfigArguments()).willReturn(emptyList());


        final RuleAction<Object> secondAction = (RuleAction<Object>) mock(RuleAction.class);
        final ResultFunction<Object> secondFunction = (ResultFunction<Object>) mock(ResultFunction.class);

        given(secondFunction.apply(any())).willAnswer(prepareResultFunctionAnswer("secondAction"));
        given(secondAction.getFunction()).willReturn(secondFunction);
        given(secondAction.getConfigArguments()).willReturn(emptyList());

        final DefaultActionRule<Object> target = new DefaultActionRule<>(
                asList(firstAction, secondAction), "analyticsKey", "modelVersion");

        // when
        final RuleResult<Object> result = target.process(VALUE);

        // then
        final Tags expectedTags = TagsImpl.of(
                asList(ActivityImpl.of("firstAction", "success", emptyList()),
                        ActivityImpl.of("secondAction", "success", emptyList())));

        assertThat(result).isEqualTo(RuleResult.of(UpdateResult.updated(VALUE), expectedTags));

        verify(firstFunction).apply(
                ResultFunctionArguments.of(
                        VALUE,
                        emptyList(),
                        InfrastructureArguments.of(emptyMap(), "analyticsKey", "default", "modelVersion")));
        verify(secondFunction).apply(
                ResultFunctionArguments.of(
                        VALUE,
                        emptyList(),
                        InfrastructureArguments.of(emptyMap(), "analyticsKey", "default", "modelVersion")));
    }

    private static Answer<RuleResult<Object>> prepareResultFunctionAnswer(String activityName) {
        return invocation -> RuleResult.of(
                UpdateResult.updated(((ResultFunctionArguments<Object>) invocation.getArgument(0)).getOperand()),
                TagsImpl.of(singletonList(ActivityImpl.of(activityName, "success", emptyList()))));
    }
}
