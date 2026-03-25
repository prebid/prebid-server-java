package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.GreenbidsConfig;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationResult;
import org.prebid.server.hooks.v1.InvocationAction;

import java.util.List;
import java.util.Map;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBanner;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBidRequest;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenImpExt;

@ExtendWith(MockitoExtension.class)
public class GreenbidsInvocationResultCreatorTest {

    @Test
    public void createGreenbidsInvocationResultWhenNotExploration() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final BidRequest bidRequest = givenBidRequest(identity(), List.of(imp));
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = givenImpsBiddersFilterMap();
        final GreenbidsConfig greenbidsConfig = givenConfig(0.0);

        // when
        final GreenbidsInvocationResult result = GreenbidsInvocationResultCreator.create(
                greenbidsConfig, bidRequest, impsBiddersFilterMap);

        // then
        final Ortb2ImpExtResult ortb2ImpExtResult = result.getAnalyticsResult().getValues().get("adunitcodevalue");
        final Map<String, Boolean> keptInAuction = ortb2ImpExtResult.getGreenbids().getKeptInAuction();

        assertThat(result.getInvocationAction()).isEqualTo(InvocationAction.update);
        assertThat(ortb2ImpExtResult).isNotNull();
        assertThat(ortb2ImpExtResult.getGreenbids().getIsExploration()).isFalse();
        assertThat(ortb2ImpExtResult.getGreenbids().getFingerprint()).isNotNull();
        assertThat(keptInAuction.get("rubicon")).isTrue();
        assertThat(keptInAuction.get("appnexus")).isFalse();
        assertThat(keptInAuction.get("pubmatic")).isFalse();
    }

    @Test
    public void createShouldReturnNoActionWhenExploration() {
        // given
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .build();

        final BidRequest bidRequest = givenBidRequest(identity(), List.of(imp));
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = givenFilterMapWithAllFilteredImps();
        final GreenbidsConfig greenbidsConfig = givenConfig(1.0);

        // when
        final GreenbidsInvocationResult result = GreenbidsInvocationResultCreator.create(
                greenbidsConfig, bidRequest, impsBiddersFilterMap);

        // then
        final Ortb2ImpExtResult ortb2ImpExtResult = result.getAnalyticsResult().getValues().get("adunitcodevalue");
        final Map<String, Boolean> keptInAuction = ortb2ImpExtResult.getGreenbids().getKeptInAuction();

        assertThat(result.getInvocationAction()).isEqualTo(InvocationAction.no_action);
        assertThat(ortb2ImpExtResult).isNotNull();
        assertThat(ortb2ImpExtResult.getGreenbids().getIsExploration()).isTrue();
        assertThat(ortb2ImpExtResult.getGreenbids().getFingerprint()).isNotNull();
        assertThat(keptInAuction.get("rubicon")).isFalse();
        assertThat(keptInAuction.get("appnexus")).isFalse();
        assertThat(keptInAuction.get("pubmatic")).isFalse();
    }

    @Test
    public void createShouldReturnRejectWhenAllImpsAreFilteredOutAndNoExploration() {
        // given
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .build();

        final BidRequest bidRequest = givenBidRequest(identity(), List.of(imp));
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = givenFilterMapWithAllFilteredImps();
        final GreenbidsConfig greenbidsConfig = givenConfig(0.001);

        // when
        final GreenbidsInvocationResult result = GreenbidsInvocationResultCreator.create(
                greenbidsConfig, bidRequest, impsBiddersFilterMap);

        // then
        final Ortb2ImpExtResult ortb2ImpExtResult = result.getAnalyticsResult().getValues().get("adunitcodevalue");
        final Map<String, Boolean> keptInAuction = ortb2ImpExtResult.getGreenbids().getKeptInAuction();

        assertThat(result.getInvocationAction()).isEqualTo(InvocationAction.reject);
        assertThat(ortb2ImpExtResult).isNotNull();
        assertThat(ortb2ImpExtResult.getGreenbids().getIsExploration()).isFalse();
        assertThat(ortb2ImpExtResult.getGreenbids().getFingerprint()).isNotNull();
        assertThat(keptInAuction.get("rubicon")).isFalse();
        assertThat(keptInAuction.get("appnexus")).isFalse();
        assertThat(keptInAuction.get("pubmatic")).isFalse();
    }

    private Map<String, Map<String, Boolean>> givenImpsBiddersFilterMap() {
        final Map<String, Boolean> biddersFitlerMap = Map.of(
                "rubicon", true,
                "appnexus", false,
                "pubmatic", false);

        return Map.of("adunitcodevalue", biddersFitlerMap);
    }

    private Map<String, Map<String, Boolean>> givenFilterMapWithAllFilteredImps() {
        final Map<String, Boolean> biddersFitlerMap = Map.of(
                "rubicon", false,
                "appnexus", false,
                "pubmatic", false);

        return Map.of("adunitcodevalue", biddersFitlerMap);
    }

    private GreenbidsConfig givenConfig(Double explorationRate) {
        return GreenbidsConfig.of("test-pbuid", 0.60, explorationRate);
    }
}
