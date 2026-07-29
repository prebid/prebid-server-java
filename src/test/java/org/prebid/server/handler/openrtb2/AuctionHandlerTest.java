package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
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
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.HooksMetricsService;
import org.prebid.server.auction.SkippedAuctionService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
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
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.ExtAnalytics;
import org.prebid.server.proto.openrtb.ext.response.ExtAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class AuctionHandlerTest extends VertxTest {

    @Mock
    private AuctionRequestFactory auctionRequestFactory;
    @Mock
    private ExchangeService exchangeService;
    @Mock(strictness = LENIENT)
    private SkippedAuctionService skippedAuctionService;
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

    private AuctionHandler target;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock(strictness = LENIENT)
    private HttpServerResponse httpResponse;
    @Mock
    private UidsCookie uidsCookie;

    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(httpResponse.exceptionHandler(any())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(skippedAuctionService.skipAuction(any()))
                .willReturn(Future.failedFuture("Auction cannot be skipped"));

        given(clock.millis()).willReturn(Instant.now().toEpochMilli());

        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbs-java/1.00");

        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.success(
                        ExitpointPayloadImpl.of(invocation.getArgument(0), invocation.getArgument(1)))));

        given(hooksMetricsService.updateHooksMetrics(any())).willAnswer(invocation -> invocation.getArgument(0));

        timeout = new TimeoutFactory(clock).create(2000L);

        target = new AuctionHandler(
                0.01,
                auctionRequestFactory,
                exchangeService,
                skippedAuctionService,
                analyticsReporterDelegator,
                metrics,
                hooksMetricsService,
                clock,
                httpInteractionLogger,
                prebidVersionProvider,
                hookStageExecutor,
                jacksonMapper);
    }

    @Test
    public void shouldSetRequestTypeMetricToAuctionContext() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

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
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

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
        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbs-java/1.00");

        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

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

        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

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
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

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
    public void shouldRespondWithServiceUnavailableIfBidRequestHasAccountBlocklisted() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.failedFuture(new BlocklistedAccountException("Blocklisted account")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(403));
        verify(httpResponse).end(eq("Blocklisted: Blocklisted account"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.blocklisted_account));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestHasAccountWithInvalidConfig() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.failedFuture(new InvalidAccountConfigException("Invalid config")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid config"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.bad_requests));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithServiceUnavailableIfBidRequestHasAppBlocklisted() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.failedFuture(new BlocklistedAppException("Blocklisted app")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(403));
        verify(httpResponse).end(eq("Blocklisted: Blocklisted app"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.blocklisted_app));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestIsInvalid() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Request is invalid"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.badinput));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithUnauthorizedIfAccountIdIsInvalid() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.failedFuture(new UnauthorizedAccountException("Account id is not provided", null)));

        // when
        target.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).end(eq("Account id is not provided"));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.err));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse, never()).end(anyString());
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithBidResponse() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(auctionContext.with(BidResponse.builder().build())));

        // when
        target.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any());
        assertThat(httpResponse.headers()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(httpResponse).end(eq("{}"));

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldRespondWithBidResponseWhenExitpointChangesHeadersAndResponse() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(auctionContext.with(BidResponse.builder().build())));
        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willReturn(Future.succeededFuture(HookStageExecutionResult.success(
                        ExitpointPayloadImpl.of(
                                MultiMap.caseInsensitiveMultiMap().add("New-Header", "New-Header-Value"),
                                "{\"response\":{}}"))));

        // when
        target.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any());
        assertThat(httpResponse.headers()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(tuple("New-Header", "New-Header-Value"));

        verify(httpResponse).end(eq("{\"response\":{}}"));

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldRespondWithCorrectResolvedRequestMediaTypePriceGranularity() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        final ExtGranularityRange granularityRange = ExtGranularityRange.of(BigDecimal.TEN, BigDecimal.ONE);
        final ExtPriceGranularity priceGranularity = ExtPriceGranularity.of(1, singletonList(granularityRange));
        final ExtMediaTypePriceGranularity priceGranuality = ExtMediaTypePriceGranularity.of(
                mapper.valueToTree(priceGranularity), null, mapper.createObjectNode());
        final BidRequest resolvedRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().mediatypepricegranularity(priceGranuality).build())
                        .auctiontimestamp(0L)
                        .build()))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .ext(ExtBidResponse.builder()
                        .debug(ExtResponseDebug.of(null, resolvedRequest, null))
                        .build())
                .build();
        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(auctionContext.with(bidResponse)));

        // when
        target.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any());
        verify(httpResponse).end(eq("{\"ext\":{\"debug\":{\"resolvedrequest\":{\"ext\":{\"prebid\":"
                + "{\"targeting\":{\"mediatypepricegranularity\":{\"banner\":{\"precision\":1,\"ranges\":"
                + "[{\"max\":10,\"increment\":1}]},\"native\":{}}},\"auctiontimestamp\":0}}}}}}"));

        verify(hookStageExecutor).executeExitpointStage(
                any(),
                eq("{\"ext\":{\"debug\":{\"resolvedrequest\":{\"ext\":{\"prebid\":"
                        + "{\"targeting\":{\"mediatypepricegranularity\":{\"banner\":{\"precision\":1,\"ranges\":"
                        + "[{\"max\":10,\"increment\":1}]},\"native\":{}}},\"auctiontimestamp\":0}}}}}}"),
                any());

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldIncrementOkOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementOkOpenrtb2AppRequestMetrics() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.succeededFuture(
                        givenAuctionContext(identity(), builder -> builder.requestTypeMetric(MetricName.openrtb2app))));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2app), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        givenHoldAuction(BidResponse.builder().build());

        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.app(App.builder().build()))));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(true), anyBoolean(), anyInt());
    }

    @Test
    public void shouldIncrementNoCookieMetrics() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        givenHoldAuction(BidResponse.builder().build());

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
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.succeededFuture(
                        givenAuctionContext(builder -> builder.imp(singletonList(Imp.builder().build())))));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(anyBoolean(), anyBoolean(), eq(1));
    }

    @Test
    public void shouldIncrementImpTypesMetrics() {
        // given
        final List<Imp> imps = singletonList(Imp.builder().build());

        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.imp(imps))));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateImpTypesMetrics(same(imps));
    }

    @Test
    public void shouldIncrementBadinputOnParsingRequestOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.badinput));
    }

    @Test
    public void shouldIncrementErrOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.err));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateRequestTimeMetric() {
        // given
        // set up clock mock to check that request_time metric has been updated with expected value
        given(clock.millis()).willReturn(5000L).willReturn(5500L);

        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

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
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
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
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        givenHoldAuction(BidResponse.builder().build());

        // simulate calling exception handler that is supposed to update networkerr timer value
        given(httpResponse.exceptionHandler(any())).willAnswer(inv -> {
            ((Handler<RuntimeException>) inv.getArgument(0)).handle(new RuntimeException());
            return httpResponse;
        });

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldNotUpdateNetworkErrorMetricIfResponseSucceeded() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        verify(metrics, never()).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldUpdateNetworkErrorMetricIfClientClosedConnection() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        givenHoldAuction(BidResponse.builder().build());

        given(routingContext.response().closed()).willReturn(true);

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfBidRequestIsInvalid() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        target.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        assertThat(auctionEvent).isEqualTo(AuctionEvent.builder()
                .httpContext(givenHttpContext())
                .status(400)
                .errors(singletonList("Invalid request format: Request is invalid"))
                .build());
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldPassInternalServerErrorEventToAnalyticsReporterIfAuctionFails() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        target.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final AuctionContext expectedAuctionContext = auctionContext.toBuilder()
                .requestTypeMetric(MetricName.openrtb2web)
                .build();

        assertThat(auctionEvent).isEqualTo(AuctionEvent.builder()
                .httpContext(givenHttpContext())
                .auctionContext(expectedAuctionContext)
                .status(500)
                .errors(singletonList("Unexpected exception"))
                .build());

        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        assertThat(auctionEvent.getHttpContext()).isEqualTo(givenHttpContext());
        assertThat(auctionEvent.getBidResponse()).isEqualTo(BidResponse.builder().build());
        assertThat(auctionEvent.getStatus()).isEqualTo(200);
        assertThat(auctionEvent.getAuctionContext().getRequestTypeMetric()).isEqualTo(MetricName.openrtb2web);
        assertThat(auctionEvent.getAuctionContext().getBidResponse()).isEqualTo(BidResponse.builder().build());

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporterWhenExitpointHookChangesResponseAndHeaders() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willReturn(Future.succeededFuture(HookStageExecutionResult.success(
                        ExitpointPayloadImpl.of(
                                MultiMap.caseInsensitiveMultiMap().add("New-Header", "New-Header-Value"),
                                "{\"response\":{}}"))));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        assertThat(auctionEvent.getHttpContext()).isEqualTo(givenHttpContext());
        assertThat(auctionEvent.getBidResponse()).isEqualTo(BidResponse.builder().build());
        assertThat(auctionEvent.getStatus()).isEqualTo(200);
        assertThat(auctionEvent.getAuctionContext().getRequestTypeMetric()).isEqualTo(MetricName.openrtb2web);
        assertThat(auctionEvent.getAuctionContext().getBidResponse()).isEqualTo(BidResponse.builder().build());

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldTolerateDuplicateQueryParamNames() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        params.add("param", "value1");
        given(httpRequest.params()).willReturn(params);
        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final CaseInsensitiveMultiMap expectedParams = CaseInsensitiveMultiMap.builder()
                .add("param", "value1")
                .build();
        assertThat(auctionEvent.getHttpContext().getQueryParams()).isEqualTo(expectedParams);
    }

    @Test
    public void shouldTolerateDuplicateHeaderNames() {
        // given
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("header", "value1");
        given(httpRequest.headers()).willReturn(headers);
        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final CaseInsensitiveMultiMap expectedHeaders = CaseInsensitiveMultiMap.builder()
                .add("header", "value1")
                .add("header", "value2")
                .build();
        assertThat(auctionEvent.getHttpContext().getHeaders()).isEqualTo(expectedHeaders);
    }

    @Test
    public void shouldSkipAuction() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext));
        given(skippedAuctionService.skipAuction(any()))
                .willReturn(Future.succeededFuture(
                        givenAuctionContext.skipAuction().with(BidResponse.builder().build())));

        // when
        target.handle(routingContext);

        // then
        verify(auctionRequestFactory, never()).enrichAuctionContext(any());
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.ok));
        verifyNoInteractions(exchangeService, analyticsReporterDelegator, hookStageExecutor);
        verify(hooksMetricsService).updateHooksMetrics(any());
        verify(httpResponse).setStatusCode(eq(200));
        verify(httpResponse).end("{}");
    }

    @Test
    public void shouldReturnSendAuctionEventWithAuctionContextBidResponseDebugInfoHoldingExitpointHookOutcome() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity()).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(HookHttpEndpoint.AMP, stageOutcomes()))
                .build();

        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

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

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final BidResponse bidResponse = auctionEvent.getBidResponse();
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
    public void shouldReturnSendAuctionEventWithAuctionContextBidResponseAnalyticsTagsHoldingExitpointHookOutcome() {
        // given
        final ObjectNode analyticsNode = mapper.createObjectNode();
        final ObjectNode optionsNode = analyticsNode.putObject("options");
        optionsNode.put("enableclientdetails", true);

        final AuctionContext givenAuctionContext = givenAuctionContext(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .analytics(analyticsNode)
                        .build()))).toBuilder()
                .hookExecutionContext(HookExecutionContext.of(HookHttpEndpoint.AMP, stageOutcomes()))
                .build();

        given(auctionRequestFactory.parseRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext));
        given(auctionRequestFactory.enrichAuctionContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));

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

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final BidResponse bidResponse = auctionEvent.getBidResponse();
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

    private AuctionContext captureAuctionContext() {
        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(exchangeService).holdAuction(captor.capture());
        return captor.getValue();
    }

    private AuctionEvent captureAuctionEvent() {
        final ArgumentCaptor<AuctionEvent> captor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(analyticsReporterDelegator).processEvent(captor.capture(), any());
        return captor.getValue();
    }

    private void givenHoldAuction(BidResponse bidResponse) {
        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(bidResponse)
                        .build()));
    }

    private AuctionContext givenAuctionContext(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return givenAuctionContext(bidRequestCustomizer, identity());
    }

    private AuctionContext givenAuctionContext(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer) {

        final BidRequest bidRequest = bidRequestCustomizer.apply(BidRequest.builder()
                .imp(emptyList())).build();

        final AuctionContext.AuctionContextBuilder auctionContextBuilder = AuctionContext.builder()
                .account(Account.builder()
                        .analytics(AccountAnalyticsConfig.of(true, null, null))
                        .build())
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .requestTypeMetric(MetricName.openrtb2web)
                .debugContext(DebugContext.of(true, false, TraceLevel.verbose))
                .hookExecutionContext(HookExecutionContext.of(HookHttpEndpoint.POST_AUCTION))
                .timeoutContext(TimeoutContext.of(0, timeout, 0));

        return auctionContextCustomizer.apply(auctionContextBuilder)
                .build();
    }

    private static HttpRequestContext givenHttpContext() {
        return HttpRequestContext.builder()
                .queryParams(CaseInsensitiveMultiMap.empty())
                .headers(CaseInsensitiveMultiMap.empty())
                .build();
    }
}
