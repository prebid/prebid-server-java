package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
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
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.VideoRequestFactory;
import org.prebid.server.auction.VideoResponseFactory;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.response.VideoResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class VideoHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private VideoRequestFactory videoRequestFactory;
    @Mock
    private VideoResponseFactory videoResponseFactory;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private AnalyticsReporter analyticsReporter;
    @Mock
    private Metrics metrics;
    @Mock
    private Clock clock;

    private VideoHandler videoHandler;
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

        given(exchangeService.holdAuction(any())).willReturn(Future.succeededFuture(BidResponse.builder().build()));

        videoHandler = new VideoHandler(videoRequestFactory, videoResponseFactory, exchangeService, analyticsReporter,
                metrics, clock, jacksonMapper);
    }

    @Test
    public void shouldSetRequestTypeMetricToAuctionContext() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        // when
        videoHandler.handle(routingContext);

        // then
        assertThat(captureAuctionContext().getRequestTypeMetric()).isNotNull();
    }

    @Test
    public void shouldUseTimeoutFromAuctionContext() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // when
        videoHandler.handle(routingContext);

        // then
        assertThat(captureAuctionContext().getTimeout().remaining()).isEqualTo(2000L);
    }

    @Test
    public void shouldComputeTimeoutBasedOnRequestProcessingStartTime() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        final Instant now = Instant.now();
        given(clock.millis()).willReturn(now.toEpochMilli()).willReturn(now.plusMillis(50L).toEpochMilli());

        // when
        videoHandler.handle(routingContext);

        // then
        assertThat(captureAuctionContext().getTimeout().remaining()).isLessThanOrEqualTo(1950L);
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestIsInvalid() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        videoHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Request is invalid"));
    }

    @Test
    public void shouldRespondWithUnauthorizedIfAccountIdIsInvalid() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new UnauthorizedAccountException("Account id is not provided")));

        // when
        videoHandler.handle(routingContext);

        // then
        verifyZeroInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).end(eq("Unauthorised: Account id is not provided"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        videoHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        videoHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).end(anyString());
    }

    @Test
    public void shouldRespondWithBidResponse() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        given(exchangeService.holdAuction(any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        given(videoResponseFactory.toVideoResponse(any(), any(), any()))
                .willReturn(VideoResponse.of(emptyList(), null, null, null));

        // when
        videoHandler.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any());
        verify(videoResponseFactory).toVideoResponse(any(), any(), any());

        assertThat(httpResponse.headers()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("Content-Type", "application/json"));
        verify(httpResponse).end(eq("{\"adPods\":[]}"));
    }

    private AuctionContext captureAuctionContext() {
        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(exchangeService).holdAuction(captor.capture());
        return captor.getValue();
    }

    private WithPodErrors<AuctionContext> givenAuctionContext(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            List<PodError> errors) {
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(BidRequest.builder()
                .imp(emptyList())).build();

        final AuctionContext auctionContext = AuctionContext.builder()
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .timeout(timeout)
                .build();

        return WithPodErrors.of(auctionContext, errors);
    }

    private static HttpContext givenHttpContext() {
        return HttpContext.builder()
                .queryParams(emptyMap())
                .headers(emptyMap())
                .cookies(emptyMap())
                .build();
    }
}
