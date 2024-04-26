package org.prebid.server.activity.infrastructure;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.infrastructure.debug.ActivityInfrastructureDebug;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.activity.infrastructure.rule.TestRule;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ActivityControllerTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ActivityInfrastructureDebug debug;

    @Test
    public void isAllowedShouldReturnDefaultResultIfAllRulesReturnsAbstain() {
        // given
        final Rule rule = TestRule.disallowIfMatches(payload -> false);
        final ActivityController activityController = ActivityController.of(true, singletonList(rule), debug);

        // when
        final boolean result = activityController.isAllowed(null);

        // then
        assertThat(result).isTrue();
        verify(debug).emitActivityInvocationDefaultResult(true);
        verify(debug).emitProcessedRule(same(rule), eq(Rule.Result.ABSTAIN));
    }

    @Test
    public void isAllowedShouldReturnFalse() {
        // given
        final ActivityController activityController = ActivityController.of(
                true,
                asList(
                        TestRule.allowIfMatches(payload -> false),
                        TestRule.disallowIfMatches(payload -> true),
                        TestRule.disallowIfMatches(payload -> false)),
                debug);

        // when
        final boolean result = activityController.isAllowed(null);

        // then
        assertThat(result).isFalse();
        verify(debug).emitActivityInvocationDefaultResult(true);
        verify(debug, times(2)).emitProcessedRule(any(), any());
    }

    @Test
    public void isAllowedShouldReturnTrue() {
        // given
        final ActivityController activityController = ActivityController.of(
                false,
                asList(
                        TestRule.allowIfMatches(payload -> false),
                        TestRule.allowIfMatches(payload -> true),
                        TestRule.disallowIfMatches(payload -> false)),
                debug);

        // when
        final boolean result = activityController.isAllowed(null);

        // then
        assertThat(result).isTrue();
        verify(debug).emitActivityInvocationDefaultResult(false);
        verify(debug, times(2)).emitProcessedRule(any(), any());
    }
}
