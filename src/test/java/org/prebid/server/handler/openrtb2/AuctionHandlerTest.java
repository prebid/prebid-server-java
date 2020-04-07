package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.manager.AdminManager;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
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
import static org.mockito.Mockito.verifyZeroInteractions;

public class AuctionHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AuctionRequestFactory auctionRequestFactory;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private AnalyticsReporter analyticsReporter;
    @Mock
    private Metrics metrics;
    @Mock
    private Clock clock;
    @Mock
    private AdminManager adminManager;

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
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(httpResponse.exceptionHandler(any())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.headers()).willReturn(new CaseInsensitiveHeaders());

        given(clock.millis()).willReturn(Instant.now().toEpochMilli());
        timeout = new TimeoutFactory(clock).create(2000L);

        auctionHandler = new AuctionHandler(
                auctionRequestFactory, exchangeService, analyticsReporter, metrics, clock, adminManager, jacksonMapper);
    }

    @Test
    public void shouldSetRequestTypeMetricToAuctionContext() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

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

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureAuctionContext().getTimeout().remaining()).isEqualTo(2000L);
    }

    @Test
    public void shouldComputeTimeoutBasedOnRequestProcessingStartTime() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        final Instant now = Instant.now();
        given(clock.millis()).willReturn(now.toEpochMilli()).willReturn(now.plusMillis(50L).toEpochMilli());

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureAuctionContext().getTimeout().remaining()).isLessThanOrEqualTo(1950L);
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
                .willReturn(Future.failedFuture(new UnauthorizedAccountException("Account id is not provided 1", "1")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verifyZeroInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).end(eq("Unauthorized: Account id is not provided 1"));
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

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any());
        assertThat(httpResponse.headers()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("Content-Type", "application/json"));
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
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().mediatypepricegranularity(priceGranuality).build())
                        .auctiontimestamp(0L)
                        .build())))
                .build();
        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder()
                        .ext(mapper.valueToTree(ExtBidResponse.of(ExtResponseDebug.of(null, resolvedRequest),
                                null, null, null, null, null)))
                        .build()));

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

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementOkOpenrtb2AppRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.app(App.builder().build()))));

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2app), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.app(App.builder().build()))));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(true), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    public void shouldIncrementNoCookieMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        given(uidsCookie.hasLiveUids()).willReturn(false);

        httpRequest.headers().add(HttpUtil.USER_AGENT_HEADER, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) "
                + "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(false), eq(false), eq(true), anyInt());
    }

    @Test
    public void shouldIncrementImpsRequestedMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(
                        givenAuctionContext(builder -> builder.imp(singletonList(Imp.builder().build())))));

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(anyBoolean(), anyBoolean(), anyBoolean(), eq(1));
    }

    @Test
    public void shouldIncrementImpTypesMetrics() {
        // given
        final List<Imp> imps = singletonList(Imp.builder().build());

        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(builder -> builder.imp(imps))));

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

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

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // simulate calling end handler that is supposed to update request_time timer value
        given(httpResponse.endHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTimeMetric(eq(500L));
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

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

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

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

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

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldIncrementRejectedMetricsIfUnknownUser() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong())).willReturn(
                Future.failedFuture(new UnauthorizedAccountException("Unauthorised account id 1", "1"))
        );
        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAccountRequestRejectedMetrics(eq("1"));
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfBidRequestIsInvalid() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(adminManager).accept(eq(AdminManager.COUNTER_KEY), any(), any());

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

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

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
        params.add("param", "value2");
        given(httpRequest.params()).willReturn(params);

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final Map<String, String> obtainedParams = auctionEvent.getHttpContext().getQueryParams();
        assertThat(obtainedParams.entrySet()).containsOnly(entry("param", "value1"));
    }

    @Test
    public void shouldTolerateDuplicateHeaderNames() {
        // given
        given(auctionRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        final CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
        headers.add("header", "value1");
        headers.add("header", "value2");
        given(httpRequest.headers()).willReturn(headers);

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        final Map<String, String> obtainedHeaders = auctionEvent.getHttpContext().getHeaders();
        assertThat(obtainedHeaders.entrySet()).containsOnly(entry("header", "value1"));
    }

    private AuctionContext captureAuctionContext() {
        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(exchangeService).holdAuction(captor.capture());
        return captor.getValue();
    }

    private AuctionEvent captureAuctionEvent() {
        final ArgumentCaptor<AuctionEvent> captor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(analyticsReporter).processEvent(captor.capture());
        return captor.getValue();
    }

    private AuctionContext givenAuctionContext(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(BidRequest.builder()
                .imp(emptyList())).build();

        return AuctionContext.builder()
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .timeout(timeout)
                .build();
    }

    private static HttpContext givenHttpContext() {
        return HttpContext.builder()
                .queryParams(emptyMap())
                .headers(emptyMap())
                .cookies(emptyMap())
                .build();
    }
}
