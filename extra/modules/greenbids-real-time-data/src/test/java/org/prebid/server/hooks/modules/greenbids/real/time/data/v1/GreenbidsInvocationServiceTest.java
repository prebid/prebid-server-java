package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.Partner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationService;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.TestBidRequestProvider.givenBanner;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.TestBidRequestProvider.givenBidRequest;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.TestBidRequestProvider.givenDevice;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.TestBidRequestProvider.givenImpExt;

@ExtendWith(MockitoExtension.class)
public class GreenbidsInvocationServiceTest {

    private JacksonMapper jacksonMapper;

    private GreenbidsInvocationService target;

    @BeforeEach
    public void setUp() {
        final ObjectMapper mapper = ObjectMapperProvider.mapper();
        jacksonMapper = new JacksonMapper(mapper);
        target = new GreenbidsInvocationService();
    }

    @Test
    public void createGreenbidsInvocationResultShouldReturnUpdateBidRequestWhenNotExploration() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt(jacksonMapper))
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
        assertThat(result.getInvocationAction()).isEqualTo(InvocationAction.update);
        assertThat(result.getUpdatedBidRequest().getImp().getFirst().getExt()
                .get("prebid").get("bidder").has("rubicon")).isTrue();
        assertThat(result.getUpdatedBidRequest().getImp().getFirst().getExt()
                .get("prebid").get("bidder").has("appnexus")).isFalse();
        assertThat(result.getAnalyticsResult().getValues().get("adunitcodevalue")
                .getGreenbids().getIsExploration()).isFalse();
    }

    @Test
    public void createGreenbidsInvocationResultShouldReturnNoActionWhenExploration() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt(jacksonMapper))
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
