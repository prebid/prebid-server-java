package org.prebid.server.activity.infrastructure.rule;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class AndRuleTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Rule allowRule;

    @Mock
    private Rule disallowRule;

    @Mock
    private Rule abstainRule;

    @Before
    public void setUp() {
        given(allowRule.proceed(any())).willReturn(Rule.Result.ALLOW);
        given(disallowRule.proceed(any())).willReturn(Rule.Result.DISALLOW);
        given(abstainRule.proceed(any())).willReturn(Rule.Result.ABSTAIN);
    }

    @Test
    public void proceedShouldReturnDisallowOnFirstOccurrence() {
        // given
        final Rule rule = new AndRule(asList(allowRule, disallowRule, abstainRule));

        // when
        final Rule.Result result = rule.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
        verifyNoInteractions(abstainRule);
    }

    @Test
    public void proceedShouldReturnAllowAndProceedAllRules() {
        // given
        final Rule rule = new AndRule(asList(allowRule, abstainRule, allowRule));

        // when
        final Rule.Result result = rule.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.ALLOW);
        verify(allowRule, times(2)).proceed(any());
        verify(abstainRule).proceed(any());
    }

    @Test
    public void proceedShouldReturnAbstain() {
        // given
        final Rule rule = new AndRule(singletonList(abstainRule));

        // when
        final Rule.Result result = rule.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.ABSTAIN);
        verify(abstainRule).proceed(any());
    }
}
