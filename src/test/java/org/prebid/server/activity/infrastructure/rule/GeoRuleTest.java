package org.prebid.server.activity.infrastructure.rule;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import org.junit.Test;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityCallPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.impl.PrivacyEnforcementServiceActivityCallPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class GeoRuleTest {

    @Test
    public void allowedShouldReturnExpectedResult() {
        // given
        final GeoRule rule = new GeoRule(null, null, true, null, null, true);

        // when
        final boolean allowed = rule.allowed();

        // then
        assertThat(allowed).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentTypesIsNull() {
        // given
        final GeoRule rule = new GeoRule(null, null, true, null, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentTypesDoesNotContainsArgument() {
        // given
        final GeoRule rule = new GeoRule(singleton(ComponentType.ANALYTICS), null, true, null, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentNamesIsNull() {
        // given
        final GeoRule rule = new GeoRule(null, null, true, null, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentNamesDoesNotContainsArgument() {
        // given
        final GeoRule rule = new GeoRule(null, singleton("other"), true, null, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnFalseIfSidsDoesNotMatched() {
        // given
        final GeoRule rule = new GeoRule(null, null, false, null, null, true);

        // when
        final boolean matches = rule.matches(ActivityCallPayloadImpl.of(ComponentType.ANALYTICS, "componentName"));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfGeoCodeWithoutRegionMatched() {
        // given
        final GeoRule rule = new GeoRule(
                null,
                null,
                true,
                singletonList(GeoRule.GeoCode.of("Country", null)),
                null,
                true);
        final ActivityCallPayload payload = PrivacyEnforcementServiceActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "country",
                "region",
                null);

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfCountryDoesNotMatched() {
        // given
        final GeoRule rule = new GeoRule(
                null,
                null,
                true,
                singletonList(GeoRule.GeoCode.of("Country", null)),
                null,
                true);
        final ActivityCallPayload payload = PrivacyEnforcementServiceActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "otherCountry",
                "region",
                null);

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfGeoCodeMatched() {
        // given
        final GeoRule rule = new GeoRule(
                null,
                null,
                true,
                singletonList(GeoRule.GeoCode.of("Country", "Region")),
                null,
                true);
        final ActivityCallPayload payload = PrivacyEnforcementServiceActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "country",
                "region",
                null);

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfRegionDoesNotMatched() {
        // given
        final GeoRule rule = new GeoRule(
                null,
                null,
                true,
                singletonList(GeoRule.GeoCode.of("Country", "Region")),
                null,
                true);
        final ActivityCallPayload payload = PrivacyEnforcementServiceActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                "country",
                "otherRegion",
                null);

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfGpcMatched() {
        // given
        final GeoRule rule = new GeoRule(null, null, true, null, "2", true);
        final ActivityCallPayload payload = BidRequestActivityCallPayload.of(
                null,
                BidRequest.builder()
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, "2")).build())
                        .build());

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfGpcNotMatched() {
        // given
        final GeoRule rule = new GeoRule(null, null, true, null, "2", true);
        final ActivityCallPayload payload = BidRequestActivityCallPayload.of(
                null,
                BidRequest.builder()
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, "1")).build())
                        .build());

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnExpectedResult() {
        // given
        final GeoRule rule = new GeoRule(
                singleton(ComponentType.BIDDER),
                singleton("bidder"),
                true,
                singletonList(GeoRule.GeoCode.of("Country", "Region")),
                "2",
                true);
        final ActivityCallPayload payload = BidRequestActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                BidRequest.builder()
                        .device(Device.builder().geo(Geo.builder().country("country").region("region").build()).build())
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, "2")).build())
                        .build());

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }
}
