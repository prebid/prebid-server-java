package org.prebid.server.activity.rule;

import org.junit.Test;
import org.prebid.server.activity.ActivityPayload;
import org.prebid.server.activity.ComponentType;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class ConditionalRuleTest {

    @Test
    public void allowedShouldReturnExpectedResult() {
        // given
        final Rule rule = ConditionalRule.of(null, null, true);

        // when
        final boolean allowed = rule.allowed();

        // then
        assertThat(allowed).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentTypesIsNull() {
        // given
        final Rule rule = ConditionalRule.of(null, null, true);

        // when
        final boolean matches = rule.matches(ActivityPayload.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentTypesDoesNotContainsArgument() {
        // given
        final Rule rule = ConditionalRule.of(singletonList(ComponentType.ANALYTICS), null, true);

        // when
        final boolean matches = rule.matches(ActivityPayload.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentNamesIsNull() {
        // given
        final Rule rule = ConditionalRule.of(singletonList(ComponentType.ANALYTICS), null, true);

        // when
        final boolean matches = rule.matches(ActivityPayload.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentNamesDoesNotContainsArgument() {
        // given
        final Rule rule = ConditionalRule.of(singletonList(ComponentType.ANALYTICS), singletonList("other"), true);

        // when
        final boolean matches = rule.matches(ActivityPayload.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnExpectedResult() {
        // given
        final Rule rule = ConditionalRule.of(singletonList(ComponentType.BIDDER), singletonList("bidder"), true);

        // when
        final boolean matches = rule.matches(ActivityPayload.of(ComponentType.BIDDER, "bidder"));

        // then
        assertThat(matches).isEqualTo(true);
    }
}
