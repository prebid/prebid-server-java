package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.HooksMetricsService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.requestfactory.AmpRequestFactory;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlocklistedAccountException;
import org.prebid.server.exception.BlocklistedAppException;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookHttpEndpoint;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.exitpoint.ExitpointPayloadImpl;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.ExtAnalytics;
import org.prebid.server.proto.openrtb.ext.response.ExtAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsActivity;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsAppliedTo;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceGroup;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStage;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStageOutcome;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class AmpHandlerTest extends VertxTest {

    @Mock
    private AmpRequestFactory ampRequestFactory;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private AnalyticsReporterDelegator analyticsReporterDelegator;
    @Mock
    private Metrics metrics;
    @Mock
    private Clock clock;
    @Mock
    private HttpInteractionLogger httpInteractionLogger;
    @Mock
    private PrebidVersionProvider prebidVersionProvider;
    @Mock(strictness = LENIENT)
    private HooksMetricsService hooksMetricsService;
    @Mock(strictness = LENIENT)
    private HookStageExecutor hookStageExecutor;

    private AmpHandler target;

    @Mock
    private RoutingContext routingContext;
    @Mock(strictness = LENIENT)
    private HttpServerRequest httpRequest;
    @Mock(strictness = LENIENT)
    private HttpServerResponse httpResponse;
    @Mock(strictness = LENIENT)
    private UidsCookie uidsCookie;

    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.getParam(anyString())).willReturn("tagId1");
        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        httpRequest.headers().add("Origin", "http://example.com");

        given(httpResponse.exceptionHandler(any())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(uidsCookie.hasLiveUids()).willReturn(true);

        given(clock.millis()).willReturn(Instant.now().toEpochMilli());

        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbs-java/1.00");

        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.success(
                        ExitpointPayloadImpl.of(invocation.getArgument(0), invocation.getArgument(1)))));

        given(hooksMetricsService.updateHooksMetrics(any())).willAnswer(invocation -> invocation.getArgument(0));

        timeout = new TimeoutFactory(clock).create(2000L);

        target = new AmpHandler(
                ampRequestFactory,
                exchangeService,
                analyticsReporterDelegator,
                metrics,
                hooksMetricsService,
                clock,
                bidderCatalog,
                singleton("bidder1"),
                new AmpResponsePostProcessor.NoOpAmpResponsePostProcessor(),
                httpInteractionLogger,
                prebidVersionProvider,
                hookStageExecutor,
                jacksonMapper,
                0);
    }

    @Test
    public void shouldSetRequestTypeMetricToAuctionContext() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        final AuctionContext auctionContext = captureAuctionContext();
        assertThat(auctionContext.getRequestTypeMetric()).isNotNull();
    }

    @Test
    public void shouldUseTimeoutFromAuctionContext() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        assertThat(captureAuctionContext())
                .extracting(AuctionContext::getTimeoutContext)
                .extracting(TimeoutContext::getTimeout)
                .extracting(Timeout::remaining)
                .isEqualTo(2000L);
    }

    @Test
    public void shouldAddPrebidVersionResponseHeader() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(BidResponse.builder().build())
                        .build()));

        // when
        target.handle(routingContext);

        // then
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("x-prebid", "pbs-java/1.00"));
    }

    @Test
    public void shouldAddObserveBrowsingTopicsResponseHeader() {
        // given
        httpRequest.headers().add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, "");

        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(BidResponse.builder().build())
                        .build()));

        // when
        target.handle(routingContext);

        // then
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("Observe-Browsing-Topics", "?1"));
    }

    @Test
    public void shouldComputeTimeoutBasedOnRequestProcessingStartTime() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        final Instant now = Instant.now();
        given(clock.millis()).willReturn(now.toEpochMilli()).willReturn(now.plusMillis(50L).toEpochMilli());

        // when
        target.handle(routingContext);

        // then
        assertThat(captureAuctionContext())
                .extracting(AuctionContext::getTimeoutContext)
                .extracting(TimeoutContext::getTimeout)
                .extracting(Timeout::remaining)
                .asInstanceOf(InstanceOfAssertFactories.LONG)
                .isLessThanOrEqualTo(1950L);
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestIsInvalid() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        target.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(400));

        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Invalid request format: Request is invalid"));
        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestHasBlocklistedAccount() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new BlocklistedAccountException("Blocklisted account")));

        // when
        target.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(403));
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Blocklisted: Blocklisted account"));
        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestHasBlocklistedApp() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new BlocklistedAppException("Blocklisted app")));

        // when
        target.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(403));
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Blocklisted: Blocklisted app"));
        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldRespondWithUnauthorizedIfAccountIdIsInvalid() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new UnauthorizedAccountException("Account id is not provided", null)));

        // when
        target.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(401));
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Account id is not provided"));
        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldRespondWithBadRequestOnInvalidAccountConfigException() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidAccountConfigException("Account is invalid")));

        // when
        target.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(400));

        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Invalid account configuration: Account is invalid"));
        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfCannotExtractBidTargeting() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", new TextNode("non-ExtBidRequest"));

        givenHoldAuction(givenBidResponse(ext));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(
                startsWith("Critical error while running the auction: Critical error while unpacking AMP targets:"));
        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse, never()).end(anyString());
        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldRespondWithExpectedResponse() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("hb_cache_id_bidder1", "value2");
        final ExtPrebid<ExtBidPrebid, Object> extPrebid = ExtPrebid.of(
                ExtBidPrebid.builder().targeting(targeting).build(),
                null);
        givenHoldAuction(givenBidResponse(mapper.valueToTree(extPrebid)));

        // when
        target.handle(routingContext);

        // then
        assertThat(httpResponse.headers()).hasSize(4)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("{\"targeting\":{\"key1\":\"value1\",\"hb_cache_id_bidder1\":\"value2\"}}"));

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{\"targeting\":{\"key1\":\"value1\",\"hb_cache_id_bidder1\":\"value2\"}}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(4)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldRespondWithExpectedResponseWhenExitpointHookChangesResponseAndHeaders() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("hb_cache_id_bidder1", "value2");
        final ExtPrebid<ExtBidPrebid, Object> extPrebid = ExtPrebid.of(
                ExtBidPrebid.builder().targeting(targeting).build(),
                null);
        givenHoldAuction(givenBidResponse(mapper.valueToTree(extPrebid)));

        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willReturn(Future.succeededFuture(HookStageExecutionResult.success(
                        ExitpointPayloadImpl.of(
                                MultiMap.caseInsensitiveMultiMap().add("New-Header", "New-Header-Value"),
                                "{\"targeting\":{\"new-key\":\"new-value\"}}"))));

        // when
        target.handle(routingContext);

        // then
        assertThat(httpResponse.headers()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("New-Header", "New-Header-Value"));
        verify(httpResponse).end(eq("{\"targeting\":{\"new-key\":\"new-value\"}}"));

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{\"targeting\":{\"key1\":\"value1\",\"hb_cache_id_bidder1\":\"value2\"}}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(4)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldRespondWithCustomTargetingIncluded() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("hb_cache_id_bidder1", "value2");
        givenHoldAuction(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .seat("bidder1")
                        .bid(singletonList(Bid.builder()
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(
                                                ExtBidPrebid.builder().targeting(targeting).build(),
                                                mapper.createObjectNode())))
                                .build()))
                        .build()))
                .build());

        final Map<String, String> customTargeting = new HashMap<>();
        customTargeting.put("rpfl_11078", "15_tier0030");
        final Bidder<?> bidder = mock(Bidder.class);
        given(bidder.extractTargeting(any())).willReturn(customTargeting);

        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        willReturn(bidder).given(bidderCatalog).bidderByName(anyString());

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).end(eq("{\"targeting\":{\"key1\":\"value1\",\"rpfl_11078\":\"15_tier0030\","
                + "\"hb_cache_id_bidder1\":\"value2\"}}"));
        verify(hookStageExecutor).executeExitpointStage(
                any(),
                eq("{\"targeting\":{\"key1\":\"value1\",\"rpfl_11078\":\"15_tier0030\","
                        + "\"hb_cache_id_bidder1\":\"value2\"}}"),
                any());

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldRespondWithAdditionalTargetingIncludedWhenSeatBidExists() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final Map<String, JsonNode> targeting =
                Map.of("key", TextNode.valueOf("value"), "test-key", TextNode.valueOf("test-value"));

        final List<Bid> bids = singletonList(Bid.builder()
                .ext(mapper.valueToTree(
                        ExtPrebid.of(
                                ExtBidPrebid.builder().build(),
                                mapper.createObjectNode())))
                .build());

        final List<SeatBid> seatBids = singletonList(SeatBid.builder()
                .seat("bidder1")
                .bid(bids)
                .build());

        final ExtBidResponsePrebid extBidResponsePrebid = ExtBidResponsePrebid.builder()
                .auctiontimestamp(1000L)
                .targeting(targeting)
                .build();

        givenHoldAuction(BidResponse.builder()
                .ext(ExtBidResponse.builder().prebid(extBidResponsePrebid).build())
                .seatbid(seatBids)
                .build());

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).end(eq("{\"targeting\":{\"key\":\"value\",\"test-key\":\"test-value\"}}"));
        verify(hookStageExecutor).executeExitpointStage(
                any(),
                eq("{\"targeting\":{\"key\":\"value\",\"test-key\":\"test-value\"}}"),
                any());
        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldRespondWithAdditionalTargetingIncludedWhenNoSeatBidExists() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final Map<String, JsonNode> targeting =
                Map.of("key", TextNode.valueOf("value"), "test-key", TextNode.valueOf("test-value"));

        final ExtBidResponsePrebid extBidResponsePrebid = ExtBidResponsePrebid.builder()
                .auctiontimestamp(1000L)
                .targeting(targeting)
                .build();

        givenHoldAuction(givenBidResponseWithExt(ExtBidResponse.builder().prebid(extBidResponsePrebid).build()));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).end(eq("{\"targeting\":{\"key\":\"value\",\"test-key\":\"test-value\"}}"));
        verify(hookStageExecutor).executeExitpointStage(
                any(),
                eq("{\"targeting\":{\"key\":\"value\",\"test-key\":\"test-value\"}}"),
                any());
        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldRespondWithDebugInfoIncludedIfTestFlagIsTrue() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(builder -> builder.id("reqId1")).toBuilder()
                .debugContext(DebugContext.of(true, true, null))
                .build();
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        givenHoldAuction(givenBidResponseWithExt(
                ExtBidResponse.builder()
                        .debug(ExtResponseDebug.of(null, auctionContext.getBidRequest(), null))
                        .prebid(ExtBidResponsePrebid.builder().auctiontimestamp(1000L).targeting(emptyMap()).build())
                        .build()));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).end(eq(
                "{\"targeting\":{},"
                        + "\"ext\":{\"debug\":{\"resolvedrequest\":{\"id\":\"reqId1\",\"imp\":[],\"tmax\":5000}}}}"));
        verify(hookStageExecutor).executeExitpointStage(
                any(),
                eq("{\"targeting\":{},"
                        + "\"ext\":{\"debug\":{\"resolvedrequest\":{\"id\":\"reqId1\",\"imp\":[],\"tmax\":5000}}}}"),
                any());
        verify(hooksMetricsService).updateHooksMetrics(any());

    }

    @Test
    public void shouldRespondWithHooksDebugAndTraceOutput() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        givenHoldAuction(givenBidResponseWithExt(
                ExtBidResponse.builder()
                        .prebid(ExtBidResponsePrebid.builder()
                                .auctiontimestamp(1000L)
                                .modules(ExtModules.of(
                                        singletonMap("module1", singletonMap("hook1", singletonList("error1"))),
                                        singletonMap("module1", singletonMap("hook1", singletonList("warning1"))),
                                        ExtModulesTrace.of(2L, emptyList())))
                                .targeting(emptyMap())
                                .build())
                        .build()));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).end(eq(
                "{\"targeting\":{},"
                        + "\"ext\":{\"prebid\":{\"modules\":{"
                        + "\"errors\":{\"module1\":{\"hook1\":[\"error1\"]}},"
                        + "\"warnings\":{\"module1\":{\"hook1\":[\"warning1\"]}},"
                        + "\"trace\":{\"executiontimemillis\":2,\"stages\":[]}}}}}"));
        verify(hookStageExecutor).executeExitpointStage(
                any(),
                eq("{\"targeting\":{},"
                        + "\"ext\":{\"prebid\":{\"modules\":{"
                        + "\"errors\":{\"module1\":{\"hook1\":[\"error1\"]}},"
                        + "\"warnings\":{\"module1\":{\"hook1\":[\"warning1\"]}},"
                        + "\"trace\":{\"executiontimemillis\":2,\"stages\":[]}}}}}"),
                any());
        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldIncrementOkAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.app(App.builder().build()))));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(true), anyBoolean(), anyInt());
    }

    @Test
    public void shouldIncrementNoCookieMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        given(uidsCookie.hasLiveUids()).willReturn(false);

        httpRequest.headers().add(HttpUtil.USER_AGENT_HEADER, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) "
                + "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(false), eq(false), anyInt());
    }

    @Test
    public void shouldIncrementImpsRequestedMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(
                        givenAuctionContext(builder -> builder.imp(singletonList(Imp.builder().build())))));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(anyBoolean(), anyBoolean(), eq(1));
    }

    @Test
    public void shouldIncrementImpsTypesMetrics() {
        // given
        final List<Imp> imps = singletonList(Imp.builder().build());

        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.imp(imps))));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateImpTypesMetrics(same(imps));
    }

    @Test
    public void shouldIncrementBadinputAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.badinput));
    }

    @Test
    public void shouldIncrementErrAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.err));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateRequestTimeMetric() {
        // given
        // set up clock mock to check that request_time metric has been updated with expected value
        given(clock.millis()).willReturn(5000L).willReturn(5500L);

        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // simulate calling end handler that is supposed to update request_time timer value
        given(httpResponse.endHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTimeMetric(eq(MetricName.request_time), eq(500L));
    }

    @Test
    public void shouldNotUpdateRequestTimeMetricIfRequestFails() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse, never()).endHandler(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateNetworkErrorMetric() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        // simulate calling exception handler that is supposed to update networkerr timer value
        given(httpResponse.exceptionHandler(any())).willAnswer(inv -> {
            ((Handler<RuntimeException>) inv.getArgument(0)).handle(new RuntimeException());
            return httpResponse;
        });

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.networkerr));
    }

    @Test
    public void shouldNotUpdateNetworkErrorMetricIfResponseSucceeded() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        // when
        target.handle(routingContext);

        // then
        verify(metrics, never()).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.networkerr));
    }

    @Test
    public void shouldUpdateNetworkErrorMetricIfClientClosedConnection() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        given(routingContext.response().closed()).willReturn(true);

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.networkerr));
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfBidRequestIsInvalid() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        target.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        assertThat(ampEvent).isEqualTo(AmpEvent.builder()
                .httpContext(givenHttpContext(singletonMap("Origin", "http://example.com")))
                .origin("http://example.com")
                .status(400)
                .errors(singletonList("Invalid request format: Request is invalid"))
                .build());

        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldPassInternalServerErrorEventToAnalyticsReporterIfAuctionFails() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        target.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        final AuctionContext expectedAuctionContext = auctionContext.toBuilder()
                .requestTypeMetric(MetricName.amp)
                .build();

        assertThat(ampEvent).isEqualTo(AmpEvent.builder()
                .httpContext(givenHttpContext(singletonMap("Origin", "http://example.com")))
                .auctionContext(expectedAuctionContext)
                .origin("http://example.com")
                .status(500)
                .errors(singletonList("Unexpected exception"))
                .build());

        verifyNoInteractions(hookStageExecutor, hooksMetricsService);
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().targeting(singletonMap("hb_cache_id_bidder1", "value1")).build(),
                        null))));

        // when
        target.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        final BidResponse expectedBidResponse = BidResponse.builder().seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(
                                        ExtBidPrebid.builder().targeting(singletonMap("hb_cache_id_bidder1", "value1"))
                                                .build(),
                                        null)))
                                .build()))
                        .build()))
                .build();

        assertThat(ampEvent.getHttpContext()).isEqualTo(givenHttpContext(singletonMap("Origin", "http://example.com")));
        assertThat(ampEvent.getBidResponse()).isEqualTo(expectedBidResponse);
        assertThat(ampEvent.getTargeting())
                .isEqualTo(singletonMap("hb_cache_id_bidder1", TextNode.valueOf("value1")));
        assertThat(ampEvent.getOrigin()).isEqualTo("http://example.com");
        assertThat(ampEvent.getStatus()).isEqualTo(200);
        assertThat(ampEvent.getAuctionContext().getRequestTypeMetric()).isEqualTo(MetricName.amp);
        assertThat(ampEvent.getAuctionContext().getBidResponse()).isEqualTo(expectedBidResponse);

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{\"targeting\":{\"hb_cache_id_bidder1\":\"value1\"}}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(4)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporterWhenExitpointHookChangesResponseAndHeaders() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willReturn(Future.succeededFuture(HookStageExecutionResult.success(
                        ExitpointPayloadImpl.of(
                                MultiMap.caseInsensitiveMultiMap().add("New-Header", "New-Header-Value"),
                                "{\"targeting\":{\"new-key\":\"new-value\"}}"))));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().targeting(singletonMap("hb_cache_id_bidder1", "value1")).build(),
                        null))));

        // when
        target.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        final BidResponse expectedBidResponse = BidResponse.builder().seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(
                                        ExtBidPrebid.builder().targeting(singletonMap("hb_cache_id_bidder1", "value1"))
                                                .build(),
                                        null)))
                                .build()))
                        .build()))
                .build();

        assertThat(ampEvent.getAuctionContext().getBidResponse()).isEqualTo(expectedBidResponse);

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{\"targeting\":{\"hb_cache_id_bidder1\":\"value1\"}}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(4)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldReturnSendAmpEventWithAuctionContextBidResponseDebugInfoHoldingExitpointHookOutcome() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity()).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(HookHttpEndpoint.AMP, stageOutcomes()))
                .build();

        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willAnswer(invocation -> {
                    final AuctionContext context = invocation.getArgument(2, AuctionContext.class);
                    final HookExecutionContext hookExecutionContext = context.getHookExecutionContext();
                    hookExecutionContext.getStageOutcomes().put(Stage.exitpoint, singletonList(StageExecutionOutcome.of(
                            "http-response",
                            singletonList(
                                    GroupExecutionOutcome.of(singletonList(
                                            HookExecutionOutcome.builder()
                                                    .hookId(HookId.of("exitpoint-module", "exitpoint-hook"))
                                                    .executionTime(4L)
                                                    .status(ExecutionStatus.success)
                                                    .message("exitpoint hook has been executed")
                                                    .action(ExecutionAction.update)
                                                    .analyticsTags(TagsImpl.of(singletonList(
                                                            ActivityImpl.of(
                                                                    "some-activity",
                                                                    "success",
                                                                    singletonList(ResultImpl.of(
                                                                            "success",
                                                                            mapper.createObjectNode(),
                                                                            givenAppliedToImpl()))))))
                                                    .build()))))));
                    return Future.succeededFuture(HookStageExecutionResult.success(
                            ExitpointPayloadImpl.of(invocation.getArgument(0), invocation.getArgument(1))));
                });

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().targeting(singletonMap("hb_cache_id_bidder1", "value1")).build(),
                        null))));

        // when
        target.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        final BidResponse bidResponse = ampEvent.getBidResponse();
        final ExtModulesTraceAnalyticsTags expectedAnalyticsTags = ExtModulesTraceAnalyticsTags.of(singletonList(
                ExtModulesTraceAnalyticsActivity.of(
                        "some-activity",
                        "success",
                        singletonList(ExtModulesTraceAnalyticsResult.of(
                                "success",
                                mapper.createObjectNode(),
                                givenExtModulesTraceAnalyticsAppliedTo())))));
        assertThat(bidResponse.getExt().getPrebid().getModules().getTrace()).isEqualTo(ExtModulesTrace.of(
                8L,
                List.of(
                        ExtModulesTraceStage.of(
                                Stage.auction_response,
                                4L,
                                singletonList(ExtModulesTraceStageOutcome.of(
                                        "auction-response",
                                        4L,
                                        singletonList(
                                                ExtModulesTraceGroup.of(
                                                        4L,
                                                        asList(
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of("module1", "hook1"))
                                                                        .executionTime(4L)
                                                                        .status(ExecutionStatus.success)
                                                                        .message("module1 hook1")
                                                                        .action(ExecutionAction.update)
                                                                        .build(),
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of("module1", "hook2"))
                                                                        .executionTime(4L)
                                                                        .status(ExecutionStatus.success)
                                                                        .message("module1 hook2")
                                                                        .action(ExecutionAction.no_action)
                                                                        .build())))))),

                        ExtModulesTraceStage.of(
                                Stage.exitpoint,
                                4L,
                                singletonList(ExtModulesTraceStageOutcome.of(
                                        "http-response",
                                        4L,
                                        singletonList(
                                                ExtModulesTraceGroup.of(
                                                        4L,
                                                        singletonList(
                                                                ExtModulesTraceInvocationResult.builder()
                                                                        .hookId(HookId.of(
                                                                                "exitpoint-module",
                                                                                "exitpoint-hook"))
                                                                        .executionTime(4L)
                                                                        .status(ExecutionStatus.success)
                                                                        .message("exitpoint hook has been executed")
                                                                        .action(ExecutionAction.update)
                                                                        .analyticsTags(expectedAnalyticsTags)
                                                                        .build())))))))));
    }

    @Test
    public void shouldReturnSendAmpEventWithAuctionContextBidResponseAnalyticsTagsHoldingExitpointHookOutcome() {
        // given
        final ObjectNode analyticsNode = mapper.createObjectNode();
        final ObjectNode optionsNode = analyticsNode.putObject("options");
        optionsNode.put("enableclientdetails", true);

        final AuctionContext auctionContext = givenAuctionContext(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .analytics(analyticsNode)
                        .build()))).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(HookHttpEndpoint.AMP, stageOutcomes()))
                .build();

        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willAnswer(invocation -> {
                    final AuctionContext context = invocation.getArgument(2, AuctionContext.class);
                    final HookExecutionContext hookExecutionContext = context.getHookExecutionContext();
                    hookExecutionContext.getStageOutcomes().put(Stage.exitpoint, singletonList(StageExecutionOutcome.of(
                            "http-response",
                            singletonList(
                                    GroupExecutionOutcome.of(singletonList(
                                            HookExecutionOutcome.builder()
                                                    .hookId(HookId.of(
                                                            "exitpoint-module",
                                                            "exitpoint-hook"))
                                                    .executionTime(4L)
                                                    .status(ExecutionStatus.success)
                                                    .message("exitpoint hook has been executed")
                                                    .action(ExecutionAction.update)
                                                    .analyticsTags(TagsImpl.of(singletonList(
                                                            ActivityImpl.of(
                                                                    "some-activity",
                                                                    "success",
                                                                    singletonList(ResultImpl.of(
                                                                            "success",
                                                                            mapper.createObjectNode(),
                                                                            givenAppliedToImpl()))))))
                                                    .build()))))));
                    return Future.succeededFuture(HookStageExecutionResult.success(
                            ExitpointPayloadImpl.of(invocation.getArgument(0), invocation.getArgument(1))));
                });

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().targeting(singletonMap("hb_cache_id_bidder1", "value1")).build(),
                        null))));

        // when
        target.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        final BidResponse bidResponse = ampEvent.getBidResponse();
        assertThat(bidResponse.getExt())
                .extracting(ExtBidResponse::getPrebid)
                .extracting(ExtBidResponsePrebid::getAnalytics)
                .extracting(ExtAnalytics::getTags)
                .asInstanceOf(InstanceOfAssertFactories.list(ExtAnalyticsTags.class))
                .hasSize(1)
                .allSatisfy(extAnalyticsTags -> {
                    assertThat(extAnalyticsTags.getStage()).isEqualTo(Stage.exitpoint);
                    assertThat(extAnalyticsTags.getModule()).isEqualTo("exitpoint-module");
                    assertThat(extAnalyticsTags.getAnalyticsTags()).isNotNull();
                });
    }

    private static AppliedToImpl givenAppliedToImpl() {
        return AppliedToImpl.builder()
                .impIds(asList("impId1", "impId2"))
                .request(true)
                .build();
    }

    private static ExtModulesTraceAnalyticsAppliedTo givenExtModulesTraceAnalyticsAppliedTo() {
        return ExtModulesTraceAnalyticsAppliedTo.builder()
                .impIds(asList("impId1", "impId2"))
                .request(true)
                .build();
    }

    private static EnumMap<Stage, List<StageExecutionOutcome>> stageOutcomes() {
        final Map<Stage, List<StageExecutionOutcome>> stageOutcomes = new HashMap<>();

        stageOutcomes.put(Stage.auction_response, singletonList(StageExecutionOutcome.of(
                "auction-response",
                singletonList(
                        GroupExecutionOutcome.of(asList(
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module1", "hook1"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .message("module1 hook1")
                                        .action(ExecutionAction.update)
                                        .build(),
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module1", "hook2"))
                                        .executionTime(4L)
                                        .message("module1 hook2")
                                        .status(ExecutionStatus.success)
                                        .action(ExecutionAction.no_action)
                                        .build()))))));

        return new EnumMap<>(stageOutcomes);
    }

    private AuctionContext givenAuctionContext(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {

        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(BidRequest.builder()
                .imp(emptyList()).tmax(5000L)).build();

        return AuctionContext.builder()
                .account(Account.builder()
                        .analytics(AccountAnalyticsConfig.of(true, null, null))
                        .build())
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .requestTypeMetric(MetricName.amp)
                .timeoutContext(TimeoutContext.of(0, timeout, 0))
                .debugContext(DebugContext.of(true, false, TraceLevel.verbose))
                .hookExecutionContext(HookExecutionContext.of(HookHttpEndpoint.AMP))
                .build();
    }

    private void givenHoldAuction(BidResponse bidResponse) {
        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(bidResponse)
                        .build()));
    }

    private static BidResponse givenBidResponse(ObjectNode extBid) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .ext(extBid)
                                .build()))
                        .build()))
                .build();
    }

    private static BidResponse givenBidResponseWithExt(ExtBidResponse extBidResponse) {
        return BidResponse.builder()
                .ext(extBidResponse)
                .build();
    }

    private AuctionContext captureAuctionContext() {
        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(exchangeService).holdAuction(captor.capture());
        return captor.getValue();
    }

    private AmpEvent captureAmpEvent() {
        final ArgumentCaptor<AmpEvent> captor = ArgumentCaptor.forClass(AmpEvent.class);
        verify(analyticsReporterDelegator).processEvent(captor.capture(), any());
        return captor.getValue();
    }

    private static HttpRequestContext givenHttpContext(Map<String, String> headers) {
        return HttpRequestContext.builder()
                .queryParams(CaseInsensitiveMultiMap.empty())
                .headers(CaseInsensitiveMultiMap.builder().addAll(headers).build())
                .build();
    }
}
