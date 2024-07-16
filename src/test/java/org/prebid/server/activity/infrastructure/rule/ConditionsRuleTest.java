package org.prebid.server.activity.infrastructure.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.PrivacyEnforcementServiceActivityInvocationPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class ConditionsRuleTest extends VertxTest {

    @Test
    public void allowedShouldReturnExpectedResult() {
        // given
        final ConditionsRule rule = new ConditionsRule(null, null, true, null, null, true);

        // when
        final boolean allowed = rule.allowed();

        // then
        assertThat(allowed).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentTypesIsNull() {
        // given
        final ConditionsRule rule = new ConditionsRule(null, null, true, null, null, true);

        // when
        final boolean matches = rule.matches(ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfComponentTypesDoesNotContainsArgument() {
        // given
        final ConditionsRule rule = new ConditionsRule(
                singleton(ComponentType.ANALYTICS),
                null,
                true,
                null,
                null,
                true);

        // when
        final boolean matches = rule.matches(ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, null));

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfComponentNamesIsNull() {
        // given
        final ConditionsRule rule = new ConditionsRule(null, null, true, null, null, true);
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
        final ConditionsRule rule = new ConditionsRule(null, singleton("other"), true, null, null, true);
        final ActivityInvocationPayload payload = ActivityInvocationPayloadImpl.of(
                ComponentType.ANALYTICS, "componentName");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnFalseIfSidsDoesNotMatched() {
        // given
        final ConditionsRule rule = new ConditionsRule(null, null, false, null, null, true);
        final ActivityInvocationPayload payload = ActivityInvocationPayloadImpl.of(
                ComponentType.ANALYTICS, "componentName");

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnTrueIfGeoCodeWithoutRegionMatched() {
        // given
        final ConditionsRule rule = new ConditionsRule(
                null,
                null,
                true,
                singletonList(ConditionsRule.GeoCode.of("Country", null)),
                null,
                true);
        final ActivityInvocationPayload payload = PrivacyEnforcementServiceActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder"),
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
        final ConditionsRule rule = new ConditionsRule(
                null,
                null,
                true,
                singletonList(ConditionsRule.GeoCode.of("Country", null)),
                null,
                true);
        final ActivityInvocationPayload payload = PrivacyEnforcementServiceActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder"),
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
        final ConditionsRule rule = new ConditionsRule(
                null,
                null,
                true,
                singletonList(ConditionsRule.GeoCode.of("Country", "Region")),
                null,
                true);
        final ActivityInvocationPayload payload = PrivacyEnforcementServiceActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder"),
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
        final ConditionsRule rule = new ConditionsRule(
                null,
                null,
                true,
                singletonList(ConditionsRule.GeoCode.of("Country", "Region")),
                null,
                true);
        final ActivityInvocationPayload payload = PrivacyEnforcementServiceActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder"),
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
        final ConditionsRule rule = new ConditionsRule(null, null, true, null, "2", true);
        final ActivityInvocationPayload payload = BidRequestActivityInvocationPayload.of(
                null,
                BidRequest.builder()
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, "2", null)).build())
                        .build());

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void matchesShouldReturnFalseIfGpcNotMatched() {
        // given
        final ConditionsRule rule = new ConditionsRule(null, null, true, null, "2", true);
        final ActivityInvocationPayload payload = BidRequestActivityInvocationPayload.of(
                null,
                BidRequest.builder()
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, "1", null)).build())
                        .build());

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(false);
    }

    @Test
    public void matchesShouldReturnExpectedResult() {
        // given
        final ConditionsRule rule = new ConditionsRule(
                singleton(ComponentType.BIDDER),
                singleton("bidder"),
                true,
                singletonList(ConditionsRule.GeoCode.of("Country", "Region")),
                "2",
                true);
        final ActivityInvocationPayload payload = BidRequestActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder"),
                BidRequest.builder()
                        .device(Device.builder().geo(Geo.builder().country("country").region("region").build()).build())
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, "2", null)).build())
                        .build());

        // when
        final boolean matches = rule.matches(payload);

        // then
        assertThat(matches).isEqualTo(true);
    }

    @Test
    public void asLogEntryShouldReturnExpectedObjectNode() {
        // given
        final ConditionsRule rule = new ConditionsRule(
                singleton(ComponentType.BIDDER),
                singleton("bidder"),
                false,
                singletonList(ConditionsRule.GeoCode.of("Country", "Region")),
                "2",
                true);

        // when
        final JsonNode result = rule.asLogEntry(mapper);

        // then
        assertThat(result.get("component_types")).containsExactly(TextNode.valueOf("BIDDER"));
        assertThat(result.get("component_names")).containsExactly(TextNode.valueOf("bidder"));
        assertThat(result.get("gpp_sids_matched")).isEqualTo(BooleanNode.getFalse());
        assertThat(result.get("geo_codes")).hasSize(1).allSatisfy(geoCode -> {
            assertThat(geoCode.get("country")).isEqualTo(TextNode.valueOf("Country"));
            assertThat(geoCode.get("region")).isEqualTo(TextNode.valueOf("Region"));
        });
        assertThat(result.get("gpc")).isEqualTo(TextNode.valueOf("2"));
        assertThat(result.get("allow")).isEqualTo(BooleanNode.getTrue());
    }
}
