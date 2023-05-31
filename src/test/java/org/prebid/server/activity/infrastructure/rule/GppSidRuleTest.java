package org.prebid.server.activity.infrastructure.rule;

import org.junit.Test;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityCallPayload;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class GppSidRuleTest {

    @Test
    public void allowedShouldReturnExpectedResult() {
        // given
        final Rule rule = new GppSidRule(null, null, true, true);

        // when
        final boolean allowed = rule.allowed();

        // then
        assertThat(allowed).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentTypesIsNull() {
        // given
        final Rule rule = new GppSidRule(null, null, true, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayload.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentTypesDoesNotContainsArgument() {
        // given
        final Rule rule = new GppSidRule(singleton(ComponentType.ANALYTICS), null, true, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayload.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentNamesIsNull() {
        // given
        final Rule rule = new GppSidRule(singleton(ComponentType.ANALYTICS), null, true, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayload.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentNamesDoesNotContainsArgument() {
        // given
        final Rule rule = new GppSidRule(singleton(ComponentType.ANALYTICS), singleton("other"), true, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayload.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnFalseIfSidsDoesNotMatched() {
        // given
        final Rule rule = new GppSidRule(null, null, false, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayload.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnExpectedResult() {
        // given
        final Rule rule = new GppSidRule(singleton(ComponentType.BIDDER), singleton("bidder"), true, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayload.of(ComponentType.BIDDER, "bidder"));

        // then
        assertThat(matches).isEqualTo(true);
    }
}
