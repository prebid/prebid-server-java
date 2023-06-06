package org.prebid.server.activity.infrastructure.creator.rule;

import org.junit.Test;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityCallPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.GeoActivityCallPayloadImpl;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.settings.model.activity.rule.AccountActivityGeoRuleConfig;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class GeoRuleCreatorTest {

    private final GeoRuleCreator target = new GeoRuleCreator();

    @Test
    public void fromShouldCreateDefaultRule() {
        // given
        final AccountActivityGeoRuleConfig config = AccountActivityGeoRuleConfig.of(null, null);
        final GppContext gppContext = GppContextCreator.from(null, null).build().getGppContext();

        // when
        final Rule rule = target.from(config, gppContext);

        // then
        assertThat(rule.matches(null)).isTrue();
        assertThat(rule.allowed()).isEqualTo(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT);
    }

    @Test
    public void fromShouldCreateExpectedRule() {
        // given
        final AccountActivityGeoRuleConfig config = AccountActivityGeoRuleConfig.of(
                AccountActivityGeoRuleConfig.Condition.of(
                        singletonList(ComponentType.BIDDER),
                        singletonList("name"),
                        asList(1, 2),
                        singletonList("country.condition")),
                false);
        final GppContext gppContext = GppContextCreator.from(null, asList(2, 3)).build().getGppContext();

        // when
        final Rule rule = target.from(config, gppContext);

        // then
        final ActivityCallPayload payload = GeoActivityCallPayloadImpl.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "name"),
                "country",
                "condition");
        assertThat(rule.matches(payload)).isTrue();
        assertThat(rule.allowed()).isFalse();
    }
}
