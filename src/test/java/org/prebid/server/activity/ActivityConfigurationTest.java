package org.prebid.server.activity;

import org.junit.Test;
import org.prebid.server.activity.rule.TestRule;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ActivityConfigurationTest {

    @Test
    public void isAllowedShouldReturnExpectedResultIfNoRulesMatched() {
        // given
        final ActivityConfiguration activityConfiguration = ActivityConfiguration.of(
                true,
                asList(
                        TestRule.allowIfMatches(payload -> false),
                        TestRule.disallowIfMatches(payload -> false)));

        // when
        final ActivityContextResult result = activityConfiguration.isAllowed(null);

        // then
        assertThat(result).isEqualTo(ActivityContextResult.of(true, 2));
    }

    @Test
    public void isAllowedShouldReturnExpectedResultIfSomeRuleMatched() {
        // given
        final ActivityConfiguration activityConfiguration = ActivityConfiguration.of(
                true,
                asList(
                        TestRule.allowIfMatches(payload -> false),
                        TestRule.disallowIfMatches(payload -> true),
                        TestRule.disallowIfMatches(payload -> false)));

        // when
        final ActivityContextResult result = activityConfiguration.isAllowed(null);

        // then
        assertThat(result).isEqualTo(ActivityContextResult.of(false, 2));
    }
}
