package org.prebid.server.activity.infrastructure.rule;

import org.junit.Test;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityCallPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.GeoActivityCallPayloadImpl;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class GeoRuleTest {

    @Test
    public void allowedShouldReturnExpectedResult() {
        // given
        final Rule rule = new GeoRule(null, null, true, null, true);

        // when
        final boolean allowed = rule.allowed();

        // then
        assertThat(allowed).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentTypesIsNull() {
        // given
        final Rule rule = new GeoRule(null, null, true, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentTypesDoesNotContainsArgument() {
        // given
        final Rule rule = new GeoRule(singleton(ComponentType.ANALYTICS), null, true, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentNamesIsNull() {
        // given
        final Rule rule = new GeoRule(null, null, true, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentNamesDoesNotContainsArgument() {
        // given
        final Rule rule = new GeoRule(null, singleton("other"), true, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnFalseIfSidsDoesNotMatched() {
        // given
        final Rule rule = new GeoRule(null, null, false, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfGeoCodeWithoutRegionMatched() {
        // given
        final Rule rule = new GeoRule(null, null, true, singletonList(GeoRule.GeoCode.of("Country", null)), true);
        final ActivityCallPayload payload = GeoActivityCallPayloadImpl.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "country",
                "region");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfCountryDoesNotMatched() {
        // given
        final Rule rule = new GeoRule(null, null, true, singletonList(GeoRule.GeoCode.of("Country", null)), true);
        final ActivityCallPayload payload = GeoActivityCallPayloadImpl.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "otherCountry",
                "region");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfGeoCodeMatched() {
        // given
        final Rule rule = new GeoRule(null, null, true, singletonList(GeoRule.GeoCode.of("Country", "Region")), true);
        final ActivityCallPayload payload = GeoActivityCallPayloadImpl.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "country",
                "region");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfRegionDoesNotMatched() {
        // given
        final Rule rule = new GeoRule(null, null, true, singletonList(GeoRule.GeoCode.of("Country", "Region")), true);
        final ActivityCallPayload payload = GeoActivityCallPayloadImpl.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "country",
                "otherRegion");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnExpectedResult() {
        // given
        final Rule rule = new GeoRule(
                singleton(ComponentType.BIDDER),
                singleton("bidder"),
                true,
                singletonList(GeoRule.GeoCode.of("Country", "Region")),
                true);
        final ActivityCallPayload payload = GeoActivityCallPayloadImpl.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "country",
                "region");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }
}
