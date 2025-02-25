package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.RejectedImp;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.FilterService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInferenceDataService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunnerWithThresholds;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.AppliedTo;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.prebid.server.auction.model.BidRejectionReason.REQUEST_BLOCKED_OPTIMIZED;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.MAPPER;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.getRubiconNode;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBidRequest;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenImpExt;

@ExtendWith(MockitoExtension.class)
public class GreenbidsRealTimeDataProcessedAuctionRequestHookTest {

    @Mock
    private FilterService filterService;

    @Mock
    private OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds;

    @Mock
    private GreenbidsInferenceDataService greenbidsInferenceDataService;

    private GreenbidsRealTimeDataProcessedAuctionRequestHook target;

    @BeforeEach
    public void setUp() {
        given(onnxModelRunnerWithThresholds.retrieveOnnxModelRunner(any()))
                .willReturn(Future.succeededFuture(mock(OnnxModelRunner.class)));
        given(onnxModelRunnerWithThresholds.retrieveThreshold(any()))
                .willReturn(Future.succeededFuture(18.2d));
        given(greenbidsInferenceDataService.extractThrottlingMessagesFromBidRequest(any()))
                .willReturn(Collections.emptyList());

        target = new GreenbidsRealTimeDataProcessedAuctionRequestHook(
                MAPPER,
                filterService,
                onnxModelRunnerWithThresholds,
                greenbidsInferenceDataService);
    }

    @Test
    public void callShouldReturnAnalyticTagsWithoutFilteringOutBiddersWhenExplorationIsTrue() {
        // given
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .build();

        final Double explorationRate = 1.0;
        final BidRequest bidRequest = givenBidRequest(identity(), List.of(imp));
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(explorationRate);

        given(filterService.filterBidders(any(), any(), any())).willReturn(Map.of("adunitcodevalue",
                Map.of("rubicon", false, "appnexus", false, "pubmatic", false)));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target
                .call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);

        final ActivityImpl actualActivity = (ActivityImpl) result.analyticsTags().activities().getFirst();
        final ResultImpl actualResult = (ResultImpl) actualActivity.results().getFirst();
        final AppliedTo acctualAppliedTo = actualResult.appliedTo();

        assertThat(acctualAppliedTo.bidders()).containsOnly("appnexus", "pubmatic", "rubicon");
        assertThat(acctualAppliedTo.impIds()).containsOnly("adunitcodevalue");
        assertThat(actualResult.values().get("adunitcodevalue").get("greenbids").get("keptInAuction"))
                .isEqualTo(MAPPER.createObjectNode()
                        .put("rubicon", false)
                        .put("appnexus", false)
                        .put("pubmatic", false));
        assertThat(actualResult.values().get("adunitcodevalue").get("greenbids").get("fingerprint").asText())
                .isNotNull();
        assertThat(actualResult.values().get("adunitcodevalue").get("tid").asText())
                .isEqualTo("67eaab5f-27a6-4689-93f7-bd8f024576e3");
        assertThat(result.rejections()).isNull();
    }

    @Test
    public void callShouldFilterBiddersBasedOnModelResultsWhenExplorationIsFalse() {
        // given
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .build();

        final Double explorationRate = 0.0001;
        final BidRequest bidRequest = givenBidRequest(identity(), List.of(imp));
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(explorationRate);

        given(filterService.filterBidders(any(), any(), any())).willReturn(Map.of("adunitcodevalue",
                Map.of("rubicon", true, "appnexus", false, "pubmatic", false)));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target
                .call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);

        final Imp expectedImp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt(getRubiconNode(), null, null))
                .build();
        assertThat(resultBidRequest).isEqualTo(givenBidRequest(identity(), List.of(expectedImp)));

        final ActivityImpl actualActivity = (ActivityImpl) result.analyticsTags().activities().getFirst();
        final ResultImpl actualResult = (ResultImpl) actualActivity.results().getFirst();
        final AppliedTo acctualAppliedTo = actualResult.appliedTo();

        assertThat(acctualAppliedTo.bidders()).containsOnly("appnexus", "pubmatic");
        assertThat(acctualAppliedTo.impIds()).containsOnly("adunitcodevalue");
        assertThat(actualResult.values().get("adunitcodevalue").get("greenbids").get("keptInAuction"))
                .isEqualTo(MAPPER.createObjectNode()
                        .put("rubicon", true)
                        .put("appnexus", false)
                        .put("pubmatic", false));
        assertThat(actualResult.values().get("adunitcodevalue").get("greenbids").get("fingerprint").asText())
                .isNotNull();
        assertThat(actualResult.values().get("adunitcodevalue").get("tid").asText())
                .isEqualTo("67eaab5f-27a6-4689-93f7-bd8f024576e3");
        assertThat(result.rejections()).containsOnly(
                entry("appnexus", List.of(RejectedImp.of("adunitcodevalue", REQUEST_BLOCKED_OPTIMIZED))),
                entry("pubmatic", List.of(RejectedImp.of("adunitcodevalue", REQUEST_BLOCKED_OPTIMIZED))));
    }

    private AuctionInvocationContext givenAuctionInvocationContext(Double explorationRate) {
        return AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                givenAccountConfig(explorationRate),
                null);
    }

    private ObjectNode givenAccountConfig(Double explorationRate) {
        final ObjectNode greenbidsNode = MAPPER.createObjectNode();
        greenbidsNode.put("enabled", true);
        greenbidsNode.put("pbuid", "test-pbuid");
        greenbidsNode.put("target-tpr", 0.99);
        greenbidsNode.put("exploration-rate", explorationRate);
        return greenbidsNode;
    }
}
