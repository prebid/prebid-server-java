package org.prebid.server.activity.infrastructure.rule;

import org.junit.Test;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class ComponentRuleTest {

    @Test
    public void allowedShouldReturnExpectedResult() {
        // given
        final ComponentRule rule = new ComponentRule(null, null, true);

        // when
        final boolean allowed = rule.allowed();

        // then
        assertThat(allowed).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentTypesIsNull() {
        // given
        final ComponentRule rule = new ComponentRule(null, null, true);

        // when
        final boolean matches = rule.matches(ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentTypesDoesNotContainsArgument() {
        // given
        final ComponentRule rule = new ComponentRule(singleton(ComponentType.ANALYTICS), null, true);

        // when
        final boolean matches = rule.matches(ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentNamesIsNull() {
        // given
        final ComponentRule rule = new ComponentRule(null, null, true);
        final ActivityInvocationPayload payload = ActivityInvocationPayloadImpl.of(
                ComponentType.ANALYTICS, "componentName");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentNamesDoesNotContainsArgument() {
        // given
        final ComponentRule rule = new ComponentRule(null, singleton("other"), true);
        final ActivityInvocationPayload payload = ActivityInvocationPayloadImpl.of(
                ComponentType.ANALYTICS, "componentName");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnExpectedResult() {
        // given
        final ComponentRule rule = new ComponentRule(singleton(ComponentType.BIDDER), singleton("bidder"), true);

        // when
        final boolean matches = rule.matches(ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder"));

        // then
        assertThat(matches).isEqualTo(true);
    }
}
