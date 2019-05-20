package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
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
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AuctionHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ExchangeService exchangeService;
    @Mock
    private AuctionRequestFactory auctionRequestFactory;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private AnalyticsReporter analyticsReporter;
    @Mock
    private Metrics metrics;
    @Mock
    private Clock clock;
    @Mock
    private TimeoutResolver timeoutResolver;

    private AuctionHandler auctionHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private UidsCookie uidsCookie;

    @Before
    public void setUp() {
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(2000L);

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        given(uidsCookieService.parseFromRequest(routingContext)).willReturn(uidsCookie);

        given(clock.millis()).willReturn(Instant.now().toEpochMilli());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);

        auctionHandler = new AuctionHandler(exchangeService, auctionRequestFactory, uidsCookieService,
                analyticsReporter, metrics, clock, timeoutFactory, timeoutResolver);
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestIsInvalid() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Request is invalid"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.failedFuture(new RuntimeException()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).end(anyString());
    }

    @Test
    public void shouldRespondWithBidResponse() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any(), any(), any(), any(), any());
        verify(httpResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end(eq("{}"));
    }

    @Test
    public void shouldUseTimeoutFromTimeoutResolver() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureTimeout().remaining()).isEqualTo(2000L);
    }

    @Test
    public void shouldComputeTimeoutBasedOnRequestProcessingStartTime() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        final Instant now = Instant.now();
        given(clock.millis()).willReturn(now.toEpochMilli()).willReturn(now.plusMillis(50L).toEpochMilli());

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureTimeout().remaining()).isEqualTo(1950L);
    }

    @Test
    public void shouldIncrementOkOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementOkOpenrtb2AppRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(builder -> builder.app(App.builder().build()))));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2app), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(builder -> builder.app(App.builder().build()))));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(true), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    public void shouldIncrementNoCookieMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        given(uidsCookie.hasLiveUids()).willReturn(false);

        httpRequest.headers().add(HttpUtil.USER_AGENT_HEADER, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
                "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(false), eq(false), eq(true), anyInt());
    }

    @Test
    public void shouldIncrementImpsRequestedMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(
                        givenBidRequest(builder -> builder.imp(singletonList(Imp.builder().build())))));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(anyBoolean(), anyBoolean(), anyBoolean(), eq(1));
    }

    @Test
    public void shouldIncrementBadinputOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.badinput));
    }

    @Test
    public void shouldIncrementErrOpenrtb2WebRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.failedFuture(new RuntimeException()));

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

        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
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
        given(auctionRequestFactory.fromRequest(any()))
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
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // simulate calling exception handler that is supposed to update networkerr timer value
        given(httpResponse.exceptionHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldNotUpdateNetworkErrorMetricIfResponseSucceeded() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics, never()).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldUpdateNetworkErrorMetricIfClientClosedConnection() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(givenBidRequest(identity())));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.openrtb2web), eq(MetricName.networkerr));
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfBidRequestIsInvalid() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        assertThat(auctionEvent).isEqualTo(AuctionEvent.builder()
                .httpContext(givenHttpContext())
                .status(400)
                .errors(singletonList("Request is invalid"))
                .build());
    }

    @Test
    public void shouldPassInternalServerErrorEventToAnalyticsReporterIfAuctionFails() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(bidRequest));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        assertThat(auctionEvent).isEqualTo(AuctionEvent.builder()
                .httpContext(givenHttpContext())
                .bidRequest(bidRequest)
                .status(500)
                .errors(singletonList("Unexpected exception"))
                .build());
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(bidRequest));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        final AuctionEvent auctionEvent = captureAuctionEvent();
        assertThat(auctionEvent).isEqualTo(AuctionEvent.builder()
                .httpContext(givenHttpContext())
                .bidRequest(bidRequest)
                .bidResponse(BidResponse.builder().build())
                .status(200)
                .errors(emptyList())
                .build());
    }

    @Test
    public void shouldTolerateDuplicateQueryParamNames() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(bidRequest));

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
        final BidRequest bidRequest = givenBidRequest(identity());
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(bidRequest));

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

    private Timeout captureTimeout() {
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(exchangeService).holdAuction(any(), any(), timeoutCaptor.capture(), any(), any());
        return timeoutCaptor.getValue();
    }

    private AuctionEvent captureAuctionEvent() {
        final ArgumentCaptor<AuctionEvent> auctionEventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(analyticsReporter).processEvent(auctionEventCaptor.capture());
        return auctionEventCaptor.getValue();
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().imp(emptyList()).tmax(1000L)).build();
    }

    private static HttpContext givenHttpContext() {
        return HttpContext.builder()
                .queryParams(emptyMap())
                .headers(emptyMap())
                .cookies(emptyMap())
                .build();
    }
}
