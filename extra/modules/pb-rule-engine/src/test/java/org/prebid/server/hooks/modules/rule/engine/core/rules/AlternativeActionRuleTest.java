package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.rule.engine.core.rules.AlternativeActionRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class AlternativeActionRuleTest {

    private static final RuleResult<Object> DELEGATE_RESULT = RuleResult.unaltered(new Object());
    private static final RuleResult<Object> ALTERNATIVE_RESULT = RuleResult.unaltered(new Object());

    @Mock(strictness = LENIENT)
    private Rule<Object, Object> delegate;

    @Mock(strictness = LENIENT)
    private Rule<Object, Object> alternative;

    private AlternativeActionRule<Object, Object> target;

    @BeforeEach
    public void setUp() {
        target = AlternativeActionRule.of(delegate, alternative);
    }

    @Test
    public void processShouldReturnDelegateResult() {
        // given
        given(delegate.process(any(), any())).willReturn(DELEGATE_RESULT);
        given(alternative.process(any(), any())).willReturn(ALTERNATIVE_RESULT);

        // when
        final RuleResult<Object> result = target.process(new Object(), new Object());

        // then
        assertThat(result).isEqualTo(DELEGATE_RESULT);
        verifyNoInteractions(alternative);
    }

    @Test
    public void processShouldReturnAlternativeResultWhenNoMatchingRuleException() {
        // given
        given(delegate.process(any(), any())).willThrow(new NoMatchingRuleException());
        given(alternative.process(any(), any())).willReturn(ALTERNATIVE_RESULT);

        // when
        final RuleResult<Object> result = target.process(new Object(), new Object());

        // then
        assertThat(result).isEqualTo(ALTERNATIVE_RESULT);
    }
}
