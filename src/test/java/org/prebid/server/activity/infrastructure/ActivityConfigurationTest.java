package org.prebid.server.activity.infrastructure;

import org.junit.Test;
import org.prebid.server.activity.infrastructure.rule.TestRule;

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
        final ActivityCallResult result = activityConfiguration.isAllowed(null);

        // then
        assertThat(result).isEqualTo(ActivityCallResult.of(true, 2));
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
        final ActivityCallResult result = activityConfiguration.isAllowed(null);

        // then
        assertThat(result).isEqualTo(ActivityCallResult.of(false, 2));
    }
}
