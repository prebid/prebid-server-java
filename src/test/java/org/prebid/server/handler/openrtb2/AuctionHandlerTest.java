package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpHeaders;
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
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;

import java.time.Clock;
import java.time.Instant;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
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
    private Metrics metrics;
    @Mock
    private Clock clock;

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
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(uidsCookieService.parseFromRequest(routingContext)).willReturn(uidsCookie);

        given(clock.millis()).willReturn(Instant.now().toEpochMilli());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);

        auctionHandler = new AuctionHandler(5000, exchangeService, auctionRequestFactory, uidsCookieService, metrics,
                clock, timeoutFactory);
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
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any())).willThrow(new RuntimeException("Unexpected exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
    }

    @Test
    public void shouldRespondWithBidResponse() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any(), any(), any());
        verify(httpResponse).putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end(eq("{}"));
    }

    @Test
    public void shouldUseTimeoutFromRequest() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).tmax(1000L).build()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureTimeout().remaining()).isEqualTo(1000L);
    }

    @Test
    public void shouldUseDefaultTimeoutIfMissingInRequest() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureTimeout().remaining()).isEqualTo(5000L);
    }

    @Test
    public void shouldComputeTimeoutBasedOnRequestProcessingStartTime() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).tmax(1000L).build()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));

        final Instant now = Instant.now();
        given(clock.millis()).willReturn(now.toEpochMilli()).willReturn(now.plusMillis(50L).toEpochMilli());

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureTimeout().remaining()).isEqualTo(950L);
    }

    @Test
    public void shouldIncrementRequestsAndOrtbRequestsMetrics() {
        // given
        givenMocksForMetricSupport();

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.requests));
        verify(metrics).incCounter(eq(MetricName.ortb_requests));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        givenMocksForMetricSupport();
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder().imp(emptyList()).app(App.builder().build()).build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.app_requests));
    }

    @Test
    public void shouldIncrementNoCookieMetrics() {
        // given
        givenMocksForMetricSupport();
        given(uidsCookie.hasLiveUids()).willReturn(true);

        httpRequest.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
                "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.safari_requests));
        verify(metrics).incCounter(eq(MetricName.safari_no_cookie_requests));
        verify(metrics).incCounter(eq(MetricName.no_cookie_requests));
    }

    @Test
    public void shouldIncrementImpsRequestedMetrics() {
        // given
        givenMocksForMetricSupport();
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder().imp(singletonList(Imp.builder().build())).build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.imps_requested), eq(1L));
    }

    @Test
    public void shouldIncrementErrorRequestMetrics() {
        // given
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
        verify(metrics).incCounter(eq(MetricName.imps_requested), eq(0L));
    }

    private void givenMocksForMetricSupport() {
        given(auctionRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));
    }

    private Timeout captureTimeout() {
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(exchangeService).holdAuction(any(), any(), timeoutCaptor.capture());
        return timeoutCaptor.getValue();
    }
}
