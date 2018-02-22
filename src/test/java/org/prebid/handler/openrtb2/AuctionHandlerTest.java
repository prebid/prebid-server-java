package org.prebid.handler.openrtb2;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
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
import org.prebid.VertxTest;
import org.prebid.auction.ExchangeService;
import org.prebid.auction.PreBidRequestContextFactory;
import org.prebid.auction.StoredRequestProcessor;
import org.prebid.cookie.UidsCookie;
import org.prebid.cookie.UidsCookieService;
import org.prebid.execution.GlobalTimeout;
import org.prebid.metric.MetricName;
import org.prebid.metric.Metrics;
import org.prebid.validation.RequestValidator;
import org.prebid.validation.ValidationResult;

import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class AuctionHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RequestValidator requestValidator;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private PreBidRequestContextFactory preBidRequestContextFactory;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private Metrics metrics;

    private AuctionHandler auctionHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private UidsCookie uidsCookie;

    @Mock
    private StoredRequestProcessor storedRequestProcessor;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(uidsCookieService.parseFromRequest(routingContext)).willReturn(uidsCookie);

        auctionHandler = new AuctionHandler(Integer.MAX_VALUE, 5000, requestValidator, exchangeService,
                storedRequestProcessor, preBidRequestContextFactory, uidsCookieService, metrics);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(1, 1, null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(1, 1, null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(1, 1, requestValidator, null, null,
                null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(1, 1, requestValidator,
                exchangeService, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(1, 1, requestValidator,
                exchangeService, storedRequestProcessor, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(1, 1, requestValidator,
                exchangeService, storedRequestProcessor, preBidRequestContextFactory, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(1, 1, requestValidator,
                exchangeService, storedRequestProcessor, preBidRequestContextFactory, uidsCookieService, null));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Incoming request has no body"));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestBodyExceedsMaxRequestSize() {
        // given
        auctionHandler = new AuctionHandler(1, 1, requestValidator, exchangeService, storedRequestProcessor,
                preBidRequestContextFactory, uidsCookieService, metrics);

        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Request size exceeded max size of 1 bytes."));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(startsWith("Invalid request format: Failed to decode:"));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestIsNotValid() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future.succeededFuture(
                BidRequest.builder().build()));

        given(requestValidator.validate(any())).willReturn(new ValidationResult(asList("error1", "error2")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: error1\nInvalid request format: error2"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future
                .succeededFuture(BidRequest.builder().build()));

        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

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
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future
                .succeededFuture(BidRequest.builder().build()));

        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

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
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future
                .succeededFuture(BidRequest.builder().build()));

        given(preBidRequestContextFactory.fromRequest(any(), any()))
                .willReturn(BidRequest.builder().tmax(1000L).build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureTimeout().remaining()).isCloseTo(1000L, offset(20L));
    }

    @Test
    public void shouldUseDefaultTimeoutIfMissingInRequest() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future
                .succeededFuture(BidRequest.builder().build()));

        given(preBidRequestContextFactory.fromRequest(any(), any()))
                .willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureTimeout().remaining()).isCloseTo(5000L, offset(20L));
    }

    @Test
    public void shouldComputeTimeoutBasedOnRequestProcessingStartTime() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willAnswer(invocation -> {
            // simulate delay introduced by processing stored requests
            TimeUnit.MILLISECONDS.sleep(50L);
            return Future.succeededFuture(BidRequest.builder().build());
        });

        given(preBidRequestContextFactory.fromRequest(any(), any()))
                .willReturn(BidRequest.builder().tmax(1000L).build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        assertThat(captureTimeout().remaining()).isCloseTo(950L, offset(20L));
    }

    @Test
    public void shouldIncrementRequestsAndOrtbRequestsMetrics() {
        // given
        givenMocksForMetricSupport();
        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.requests));
        verify(metrics).incCounter(eq(MetricName.open_rtb_requests));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        givenMocksForMetricSupport();
        given(preBidRequestContextFactory.fromRequest(any(), any()))
                .willReturn(BidRequest.builder().app(App.builder().build()).build());

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
    public void shouldIncrementErrorRequestMetrics() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("invalid"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    private void givenMocksForMetricSupport() {
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future
                .succeededFuture(BidRequest.builder().build()));

        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));
    }

    private GlobalTimeout captureTimeout() {
        final ArgumentCaptor<GlobalTimeout> timeoutCaptor = ArgumentCaptor.forClass(GlobalTimeout.class);
        verify(exchangeService).holdAuction(any(), any(), timeoutCaptor.capture());
        return timeoutCaptor.getValue();
    }
}
