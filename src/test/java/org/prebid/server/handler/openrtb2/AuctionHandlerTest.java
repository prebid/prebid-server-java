package org.prebid.server.handler.openrtb2;

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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
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
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class AuctionHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AuctionRequestFactory auctionRequestFactory;
    @Mock
    private ExchangeService exchangeService;
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

    private AuctionHandler auctionHandler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private UidsCookie uidsCookie;

    private Timeout timeout;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(httpResponse.exceptionHandler(any())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(clock.millis()).willReturn(Instant.now().toEpochMilli());

        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbs-java/1.00");

        timeout = new TimeoutFactory(clock).create(2000L);

        auctionHandler = new AuctionHandler(
                0.01,
                auctionRequestFactory,
                exchangeService,
                analyticsReporterDelegator,
                metrics,
                clock,
                httpInteractionLogger,
                prebidVersionProvider,
                jacksonMapper);
    }

    @Test
    public void shouldSetRequestTypeMetricToAuctionContext() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionContext auctionContext = captureAuctionContext();
        assertThat(auctionContext.getRequestTypeMetric()).isNotNull();
    }

    @Test
    public void shouldUseTimeoutFromAuctionContext() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

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

        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(BidResponse.builder().build())
                        .build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("x-prebid", "pbs-java/1.00"));
    }

    @Test
    public void shouldAddObserveBrowsingTopicsResponseHeader() {
        // given
        httpRequest.headers().add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, "");

        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(BidResponse.builder().build())
                        .build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("Observe-Browsing-Topics", "?1"));
    }

    @Test
    public void shouldComputeTimeoutBasedOnRequestProcessingStartTime() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        final Instant now = Instant.now();
        given(clock.millis()).willReturn(now.toEpochMilli()).willReturn(now.plusMillis(50L).toEpochMilli());

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureAuctionContext())
                .extracting(AuctionContext::getTimeoutContext)
                .extracting(TimeoutContext::getTimeout)
                .extracting(Timeout::remaining)
                .asInstanceOf(InstanceOfAssertFactories.LONG)
                .isLessThanOrEqualTo(1950L);
    }

    @Test
    public void shouldRespondWithServiceUnavailableIfBidRequestHasAccountBlacklisted() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new BlacklistedAccountException("Blacklisted account")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(403));
        verify(httpResponse).end(eq("Blacklisted: Blacklisted account"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.blacklisted_account));
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestHasAccountWithInvalidConfig() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidAccountConfigException("Invalid config")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid config"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.bad_requests));
    }

    @Test
    public void shouldRespondWithServiceUnavailableIfBidRequestHasAppBlacklisted() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new BlacklistedAppException("Blacklisted app")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(403));
        verify(httpResponse).end(eq("Blacklisted: Blacklisted app"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.blacklisted_app));
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestIsInvalid() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Request is invalid"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.badinput));
    }

    @Test
    public void shouldRespondWithUnauthorizedIfAccountIdIsInvalid() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new UnauthorizedAccountException("Account id is not provided", null)));

        // when
        auctionHandler.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).end(eq("Account id is not provided"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));

        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.err));
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).end(anyString());
    }

    @Test
    public void shouldRespondWithBidResponse() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidResponse(BidResponse.builder().build())
                .build();
        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(auctionContext));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any());
        assertThat(httpResponse.headers()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(httpResponse).end(eq("{}"));
    }

    @Test
    public void shouldRespondWithCorrectResolvedRequestMediaTypePriceGranularity() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

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
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidResponse(bidResponse)
                .build();
        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(auctionContext));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any());
        verify(httpResponse).end(eq("{\"ext\":{\"debug\":{\"resolvedrequest\":{\"ext\":{\"prebid\":"
                + "{\"targeting\":{\"mediatypepricegranularity\":{\"banner\":{\"precision\":1,\"ranges\":"
                + "[{\"max\":10,\"increment\":1}]},\"native\":{}}},\"auctiontimestamp\":0}}}}}}"));
    }

    @Test
    public void shouldIncrementOkOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementOkOpenrtb2AppRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong())).willReturn(Future.succeededFuture(
                givenAuctionContext(identity(), builder -> builder.requestTypeMetric(MetricName.openrtb2app))));

        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2app), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        givenHoldAuction(BidResponse.builder().build());

        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.app(App.builder().build()))));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(true), anyBoolean(), anyInt());
    }

    @Test
    public void shouldIncrementNoCookieMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        given(uidsCookie.hasLiveUids()).willReturn(false);

        httpRequest.headers().add(HttpUtil.USER_AGENT_HEADER, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) "
                + "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(false), eq(false), anyInt());
    }

    @Test
    public void shouldIncrementImpsRequestedMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(
                        givenAuctionContext(builder -> builder.imp(singletonList(Imp.builder().build())))));

        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(anyBoolean(), anyBoolean(), eq(1));
    }

    @Test
    public void shouldIncrementImpTypesMetrics() {
        // given
        final List<Imp> imps = singletonList(Imp.builder().build());

        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.imp(imps))));

        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateImpTypesMetrics(same(imps));
    }

    @Test
    public void shouldIncrementBadinputOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.badinput));
    }

    @Test
    public void shouldIncrementErrOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.err));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateRequestTimeMetric() {
        // given

        // set up clock mock to check that request_time metric has been updated with expected value
        given(clock.millis()).willReturn(5000L).willReturn(5500L);

        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // simulate calling end handler that is supposed to update request_time timer value
        given(httpResponse.endHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTimeMetric(eq(MetricName.request_time), eq(500L));
    }

    @Test
    public void shouldNotUpdateRequestTimeMetricIfRequestFails() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).endHandler(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateNetworkErrorMetric() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // simulate calling exception handler that is supposed to update networkerr timer value
        given(httpResponse.exceptionHandler(any())).willAnswer(inv -> {
            ((Handler<RuntimeException>) inv.getArgument(0)).handle(new RuntimeException());
            return httpResponse;
        });

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldNotUpdateNetworkErrorMetricIfResponseSucceeded() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics, never()).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldUpdateNetworkErrorMetricIfClientClosedConnection() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        given(routingContext.response().closed()).willReturn(true);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfBidRequestIsInvalid() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        assertThat(auctionEvent).isEqualTo(AuctionEvent.builder()
                .httpContext(givenHttpContext())
                .status(400)
                .errors(singletonList("Invalid request format: Request is invalid"))
                .build());
    }

    @Test
    public void shouldPassInternalServerErrorEventToAnalyticsReporterIfAuctionFails() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        auctionHandler.handle(routingContext);

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
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final AuctionContext expectedAuctionContext = auctionContext.toBuilder()
                .requestTypeMetric(MetricName.openrtb2web)
                .bidResponse(BidResponse.builder().build())
                .build();

        assertThat(auctionEvent).isEqualTo(AuctionEvent.builder()
                .httpContext(givenHttpContext())
                .auctionContext(expectedAuctionContext)
                .bidResponse(BidResponse.builder().build())
                .status(200)
                .errors(emptyList())
                .build());
    }

    @Test
    public void shouldTolerateDuplicateQueryParamNames() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        params.add("param", "value1");
        given(httpRequest.params()).willReturn(params);
        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

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
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("header", "value1");
        given(httpRequest.headers()).willReturn(headers);
        givenHoldAuction(BidResponse.builder().build());

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final CaseInsensitiveMultiMap expectedHeaders = CaseInsensitiveMultiMap.builder()
                .add("header", "value1")
                .add("header", "value2")
                .build();
        assertThat(auctionEvent.getHttpContext().getHeaders()).isEqualTo(expectedHeaders);
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
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .requestTypeMetric(MetricName.openrtb2web)
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
