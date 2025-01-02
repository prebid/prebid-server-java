package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.Partner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationResult;
import org.prebid.server.hooks.v1.InvocationAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBanner;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBidRequest;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenDevice;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenImpExt;

@ExtendWith(MockitoExtension.class)
public class GreenbidsInvocationServiceTest {

    private GreenbidsInvocationService target;

    @BeforeEach
    public void setUp() {
        target = new GreenbidsInvocationService();
    }

    @Test
    public void createGreenbidsInvocationResultShouldReturnUpdateBidRequestWhenNotExploration() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDevice(identity());
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, null);
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = givenImpsBiddersFilterMap();
        final Partner partner = givenPartner(0.0);

        // when
        final GreenbidsInvocationResult result = target.createGreenbidsInvocationResult(
                partner, bidRequest, impsBiddersFilterMap);

        // then
        final JsonNode updatedBidRequestExtPrebidBidders = result.getUpdatedBidRequest().getImp().getFirst().getExt()
                .get("prebid").get("bidder");
        final Ortb2ImpExtResult ortb2ImpExtResult = result.getAnalyticsResult().getValues().get("adunitcodevalue");
        final Map<String, Boolean> keptInAuction = ortb2ImpExtResult.getGreenbids().getKeptInAuction();

        assertThat(result.getInvocationAction()).isEqualTo(InvocationAction.update);
        assertThat(updatedBidRequestExtPrebidBidders.has("rubicon")).isTrue();
        assertThat(updatedBidRequestExtPrebidBidders.has("appnexus")).isFalse();
        assertThat(updatedBidRequestExtPrebidBidders.has("pubmatic")).isFalse();
        assertThat(ortb2ImpExtResult).isNotNull();
        assertThat(ortb2ImpExtResult.getGreenbids().getIsExploration()).isFalse();
        assertThat(ortb2ImpExtResult.getGreenbids().getFingerprint()).isNotNull();
        assertThat(keptInAuction.get("rubicon")).isTrue();
        assertThat(keptInAuction.get("appnexus")).isFalse();
        assertThat(keptInAuction.get("pubmatic")).isFalse();

    }

    @Test
    public void createGreenbidsInvocationResultShouldReturnNoActionWhenExploration() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDevice(identity());
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, null);
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = givenImpsBiddersFilterMap();
        final Partner partner = givenPartner(1.0);

        // when
        final GreenbidsInvocationResult result = target.createGreenbidsInvocationResult(
                partner, bidRequest, impsBiddersFilterMap);

        // then
        final JsonNode updatedBidRequestExtPrebidBidders = result.getUpdatedBidRequest().getImp().getFirst().getExt()
                .get("prebid").get("bidder");
        final Ortb2ImpExtResult ortb2ImpExtResult = result.getAnalyticsResult().getValues().get("adunitcodevalue");
        final Map<String, Boolean> keptInAuction = ortb2ImpExtResult.getGreenbids().getKeptInAuction();

        assertThat(result.getInvocationAction()).isEqualTo(InvocationAction.no_action);
        assertThat(updatedBidRequestExtPrebidBidders.has("rubicon")).isTrue();
        assertThat(updatedBidRequestExtPrebidBidders.has("appnexus")).isTrue();
        assertThat(updatedBidRequestExtPrebidBidders.has("pubmatic")).isTrue();
        assertThat(ortb2ImpExtResult).isNotNull();
        assertThat(ortb2ImpExtResult.getGreenbids().getIsExploration()).isTrue();
        assertThat(ortb2ImpExtResult.getGreenbids().getFingerprint()).isNotNull();
        assertThat(keptInAuction.get("rubicon")).isTrue();
        assertThat(keptInAuction.get("appnexus")).isTrue();
        assertThat(keptInAuction.get("pubmatic")).isTrue();
    }

    private Map<String, Map<String, Boolean>> givenImpsBiddersFilterMap() {
        final Map<String, Boolean> biddersFitlerMap = new HashMap<>();
        biddersFitlerMap.put("rubicon", true);
        biddersFitlerMap.put("appnexus", false);
        biddersFitlerMap.put("pubmatic", false);

        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = new HashMap<>();
        impsBiddersFilterMap.put("adunitcodevalue", biddersFitlerMap);

        return impsBiddersFilterMap;
    }

    private Partner givenPartner(Double explorationRate) {
        return Partner.of("test-pbuid", 0.60, explorationRate);
    }
}
