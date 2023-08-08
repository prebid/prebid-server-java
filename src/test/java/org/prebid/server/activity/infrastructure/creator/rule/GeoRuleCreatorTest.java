package org.prebid.server.activity.infrastructure.creator.rule;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import org.junit.Test;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityCallPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityCallPayload;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
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
        final ActivityControllerCreationContext creationContext = creationContext(gppContext);

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ALLOW);
    }

    @Test
    public void fromShouldCreateExpectedRule() {
        // given
        final AccountActivityGeoRuleConfig config = AccountActivityGeoRuleConfig.of(
                AccountActivityGeoRuleConfig.Condition.of(
                        singletonList(ComponentType.BIDDER),
                        singletonList("name"),
                        asList(1, 2),
                        asList(null, "country1", "country2.", "country3.region"),
                        "2"),
                false);
        final GppContext gppContext = GppContextCreator.from(null, asList(2, 3)).build().getGppContext();
        final ActivityControllerCreationContext creationContext = creationContext(gppContext);

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        final ActivityCallPayload payload1 = BidRequestActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "name"),
                givenBidRequest("country1", "region", "2"));
        assertThat(rule.proceed(payload1)).isEqualTo(Rule.Result.DISALLOW);

        final ActivityCallPayload payload2 = BidRequestActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "name"),
                givenBidRequest("country2", "region", "2"));
        assertThat(rule.proceed(payload2)).isEqualTo(Rule.Result.DISALLOW);

        final ActivityCallPayload payload3 = BidRequestActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "name"),
                givenBidRequest("country3", "region", "2"));
        assertThat(rule.proceed(payload3)).isEqualTo(Rule.Result.DISALLOW);

        final ActivityCallPayload payload4 = BidRequestActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.BIDDER, "name"),
                givenBidRequest("country1", null, "2"));
        assertThat(rule.proceed(payload4)).isEqualTo(Rule.Result.DISALLOW);
    }

    private static BidRequest givenBidRequest(String country, String region, String gpc) {
        return BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country(country).region(region).build()).build())
                .regs(Regs.builder().ext(ExtRegs.of(null, null, gpc)).build())
                .build();
    }

    private static ActivityControllerCreationContext creationContext(GppContext gppContext) {
        return ActivityControllerCreationContext.of(null, null, gppContext);
    }
}
