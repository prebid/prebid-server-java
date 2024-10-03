package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.Partner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationService;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.json.JacksonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHookTest.givenBanner;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHookTest.givenDevice;

public class GreenbidsInvocationServiceTest {

    private JacksonMapper jacksonMapper;

    private TestBidRequestProvider testBidRequestProvider;

    private GreenbidsInvocationService target;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        final ObjectMapper mapper = new ObjectMapper();
        jacksonMapper = new JacksonMapper(mapper);
        testBidRequestProvider = new TestBidRequestProvider(jacksonMapper);
        target = new GreenbidsInvocationService();
    }

    @Test
    public void shouldReturnUpdateBidRequestWhenNotExploration() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(testBidRequestProvider.givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final BidRequest bidRequest = testBidRequestProvider
                .givenBidRequest(request -> request, List.of(imp), device, null);
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = givenImpsBiddersFilterMap();
        final Partner partner = givenPartner(0.0);

        // when
        final GreenbidsInvocationResult result = target.createGreenbidsInvocationResult(
                partner, bidRequest, impsBiddersFilterMap);

        // then
        assertThat(result.getInvocationAction()).isEqualTo(InvocationAction.update);
        assertThat(result.getUpdatedBidRequest().getImp().getFirst().getExt()
                .get("prebid").get("bidder").has("rubicon")).isTrue();
        assertThat(result.getUpdatedBidRequest().getImp().getFirst().getExt()
                .get("prebid").get("bidder").has("appnexus")).isFalse();
        assertThat(result.getAnalyticsResult().getValues().get("adunitcodevalue")
                .getGreenbids().getIsExploration()).isFalse();
    }

    @Test
    public void shouldReturnNoActionWhenExploration() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(testBidRequestProvider.givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final BidRequest bidRequest = testBidRequestProvider
                .givenBidRequest(request -> request, List.of(imp), device, null);
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = givenImpsBiddersFilterMap();
        final Partner partner = givenPartner(1.0);

        // when
        final GreenbidsInvocationResult result = target.createGreenbidsInvocationResult(
                partner, bidRequest, impsBiddersFilterMap);

        // then
        assertThat(result.getInvocationAction()).isEqualTo(InvocationAction.no_action);
        assertThat(result.getUpdatedBidRequest().getImp().getFirst().getExt()
                .get("prebid").get("bidder").has("rubicon")).isTrue();
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
