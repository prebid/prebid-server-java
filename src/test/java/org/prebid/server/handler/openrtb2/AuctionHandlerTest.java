package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
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
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.prebid.RequestHandlerMetrics;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
    private Clock clock;
    @Mock
    private org.prebid.server.metric.Metrics metrics;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private UidsCookie uidsCookie;

    private AuctionHandler auctionHandler;
    private RequestHandlerMetrics handlerMetrics;

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

        handlerMetrics = spy(new RequestHandlerMetrics(metrics, clock));

        auctionHandler = new AuctionHandler(5000, exchangeService, auctionRequestFactory, uidsCookieService,
                handlerMetrics, clock, timeoutFactory);
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestIsInvalid() {
        // given
        final InvalidRequestException ex = new InvalidRequestException("Request is invalid");
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.failedFuture(ex));

        // when
        auctionHandler.handle(routingContext);

        // then
        verifyZeroInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Request is invalid"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        BidRequest bidRequest = BidRequest.builder().build();
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.succeededFuture(bidRequest));

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
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.succeededFuture(BidRequest.builder().build()));

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
        final BidRequest bidRequest = BidRequest.builder().tmax(1000L).build();
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.succeededFuture(bidRequest));

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
        BidRequest bidRequest = BidRequest.builder().build();
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.succeededFuture(bidRequest));

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
        final BidRequest bidRequest = BidRequest.builder().tmax(1000L).build();
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.succeededFuture(bidRequest));

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
        verify(handlerMetrics).updateRequestMetrics(routingContext, auctionHandler);
        verify(metrics).incCounter(eq(MetricName.requests));
        verify(metrics).incCounter(eq(MetricName.open_rtb_requests));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        BidRequest bidRequest = BidRequest.builder().app(App.builder().build()).build();
        givenMocksForMetricSupport();
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.succeededFuture(bidRequest));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(handlerMetrics).updateAppAndNoCookieMetrics(routingContext, auctionHandler, bidRequest, false, true);
        verify(metrics).incCounter(eq(MetricName.app_requests));
    }

    @Test
    public void shouldIncrementNoCookieMetrics() {
        // given
        final boolean hasLiveUids = true;
        Tuple2<BidRequest, BidResponse> requestBidResponseTuple = givenMocksForMetricSupport();
        given(uidsCookie.hasLiveUids()).willReturn(hasLiveUids);

        httpRequest.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
                "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(handlerMetrics).updateAppAndNoCookieMetrics(routingContext, auctionHandler, requestBidResponseTuple.getLeft(),
                hasLiveUids, requestBidResponseTuple.getLeft().getApp() != null);
        verify(metrics).incCounter(eq(MetricName.safari_requests));
        verify(metrics).incCounter(eq(MetricName.safari_no_cookie_requests));
        verify(metrics).incCounter(eq(MetricName.no_cookie_requests));
    }

    @Test
    public void shouldIncrementErrorRequestMetrics() {
        // given
        final InvalidRequestException ex = new InvalidRequestException("Request is invalid");
        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.failedFuture(ex));

        // when
        auctionHandler.handle(routingContext);

        // then
        verifyZeroInteractions(exchangeService);
        verify(handlerMetrics).updateErrorRequestsMetric(routingContext, auctionHandler, ex);
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    private Tuple2<BidRequest, BidResponse> givenMocksForMetricSupport() {
        Tuple2<BidRequest, BidResponse> tuple2 = Tuple2.of(BidRequest.builder().build(), BidResponse.builder().build());

        given(auctionRequestFactory.fromRequest(any())).willReturn(Future.succeededFuture(tuple2.getLeft()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(tuple2.getRight()));
        return tuple2;
    }

    private Timeout captureTimeout() {
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(exchangeService).holdAuction(any(), any(), timeoutCaptor.capture());
        return timeoutCaptor.getValue();
    }
}
