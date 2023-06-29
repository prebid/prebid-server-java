package org.prebid.server.activity.infrastructure.rule;

import org.junit.Test;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityCallPayloadImpl;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class ComponentRuleTest {

    @Test
    public void allowedShouldReturnExpectedResult() {
        // given
        final Rule rule = new ComponentRule(null, null, true);

        // when
        final boolean allowed = rule.allowed();

        // then
        assertThat(allowed).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentTypesIsNull() {
        // given
        final Rule rule = new ComponentRule(null, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentTypesDoesNotContainsArgument() {
        // given
        final Rule rule = new ComponentRule(singleton(ComponentType.ANALYTICS), null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentNamesIsNull() {
        // given
        final Rule rule = new ComponentRule(null, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentNamesDoesNotContainsArgument() {
        // given
        final Rule rule = new ComponentRule(null, singleton("other"), true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnExpectedResult() {
        // given
        final Rule rule = new ComponentRule(singleton(ComponentType.BIDDER), singleton("bidder"), true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"));

        // then
        assertThat(matches).isEqualTo(true);
    }
}
