package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class AmpHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AmpRequestFactory ampRequestFactory;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private AnalyticsReporter analyticsReporter;
    @Mock
    private Metrics metrics;
    @Mock
    private Clock clock;

    private AmpHandler ampHandler;

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

        given(httpRequest.getParam(anyString())).willReturn("tagId1");

        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.putHeader(anyString(), eq((String) null))).willReturn(httpResponse);
        given(httpResponse.putHeader(anyString(), anyString())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        httpRequest.headers().add("Origin", "http://example.com");

        given(uidsCookieService.parseFromRequest(routingContext)).willReturn(uidsCookie);

        given(clock.millis()).willReturn(Instant.now().toEpochMilli());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        ampHandler = new AmpHandler(5000, ampRequestFactory, exchangeService, uidsCookieService,
                singleton("bidder1"), bidderCatalog, analyticsReporter,
                new AmpResponsePostProcessor.NoOpAmpResponsePostProcessor(), metrics, clock, timeoutFactory);
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestIsInvalid() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        ampHandler.handle(routingContext);

        // then
        verifyZeroInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).putHeader("AMP-Access-Control-Allow-Source-Origin", "http://example.com");
        verify(httpResponse).putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
        verify(httpResponse).end(eq("Invalid request format: Request is invalid"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).putHeader("AMP-Access-Control-Allow-Source-Origin", "http://example.com");
        verify(httpResponse).putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfCannotExtractBidTargeting() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", new TextNode("non-ExtBidRequest"));
        given(exchangeService.holdAuction(any(), any(), any(), any(), any())).willReturn(givenBidResponse(ext));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).putHeader("AMP-Access-Control-Allow-Source-Origin", "http://example.com");
        verify(httpResponse).putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
        verify(httpResponse).end(
                startsWith("Critical error while running the auction: Critical error while unpacking AMP targets:"));
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(ampRequestFactory.fromRequest(any())).willReturn(Future.failedFuture(new RuntimeException()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).end(anyString());
    }

    @Test
    public void shouldRespondWithExpectedResponse() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("hb_cache_id_bidder1", "value2");
        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(
                        mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null, targeting, null), null))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).putHeader("AMP-Access-Control-Allow-Source-Origin", "http://example.com");
        verify(httpResponse).putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
        verify(httpResponse).putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end(eq("{\"targeting\":{\"key1\":\"value1\",\"hb_cache_id_bidder1\":\"value2\"}}"));
    }

    @Test
    public void shouldRespondWithCustomTargetingIncluded() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("hb_cache_id_bidder1", "value2");
        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder()
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("bidder1")
                                .bid(singletonList(Bid.builder()
                                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null, targeting, null),
                                                mapper.createObjectNode())))
                                        .build()))
                                .build()))
                        .build()));

        final Map<String, String> customTargeting = new HashMap<>();
        customTargeting.put("rpfl_11078", "15_tier0030");
        final Bidder<?> bidder = mock(Bidder.class);
        given(bidder.extractTargeting(any())).willReturn(customTargeting);

        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        willReturn(bidder).given(bidderCatalog).bidderByName(anyString());

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).putHeader("AMP-Access-Control-Allow-Source-Origin", "http://example.com");
        verify(httpResponse).putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
        verify(httpResponse).putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end(eq("{\"targeting\":{\"key1\":\"value1\",\"rpfl_11078\":\"15_tier0030\"," +
                "\"hb_cache_id_bidder1\":\"value2\"}}"));
    }

    @Test
    public void shouldRespondWithDebugInfoIncluded() {
        // given
        final BidRequest bidRequest = BidRequest.builder().id("reqId1").test(1).imp(emptyList()).build();
        given(ampRequestFactory.fromRequest(any())).willReturn(Future.succeededFuture(bidRequest));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponseWithExt(mapper.valueToTree(
                        ExtBidResponse.of(ExtResponseDebug.of(null, bidRequest),
                                null, null, null, null))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).end(
                eq("{\"targeting\":{},\"debug\":{\"resolvedrequest\":{\"id\":\"reqId1\",\"imp\":[],\"test\":1}}}"));
    }

    @Test
    public void shouldIncrementOkAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, null, null), null))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.ok));
    }

    @Test
    public void shouldIncrementAppRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder().imp(emptyList()).app(App.builder().build()).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, null, null), null))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(true), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    public void shouldIncrementNoCookieMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, null, null), null))));

        given(uidsCookie.hasLiveUids()).willReturn(false);

        httpRequest.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
                "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(eq(false), eq(false), eq(true), anyInt());
    }

    @Test
    public void shouldIncrementImpsRequestedMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder().imp(singletonList(Imp.builder().build())).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, null, null), null))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateAppAndNoCookieAndImpsRequestedMetrics(anyBoolean(), anyBoolean(), anyBoolean(), eq(1));
    }

    @Test
    public void shouldIncrementBadinputAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.badinput));
    }

    @Test
    public void shouldIncrementErrAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any())).willReturn(Future.failedFuture(new RuntimeException()));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.err));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateRequestTimeMetric() {
        // given

        // set up clock mock to check that request_time metric has been updated with expected value
        given(clock.millis()).willReturn(5000L).willReturn(5500L);

        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidResponse.builder().build()));

        // simulate calling end handler that is supposed to update request_time timer value
        given(httpResponse.endHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTimeMetric(eq(500L));
    }

    @Test
    public void shouldNotUpdateRequestTimeMetricIfRequestFails() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).endHandler(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUpdateNetworkErrorMetric() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, null, null), null))));

        // simulate calling exception handler that is supposed to update networkerr timer value
        given(httpResponse.exceptionHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.networkerr));
    }

    @Test
    public void shouldNotUpdateNetworkErrorMetricIfResponseSucceeded() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, null, null), null))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics, never()).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.networkerr));
    }

    @Test
    public void shouldUpdateNetworkErrorMetricIfClientClosedConnection() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(emptyList()).build()));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, null, null), null))));

        given(routingContext.response().closed()).willReturn(true);

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.networkerr));
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfBidRequestIsInvalid() {
        // given
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        ampHandler.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        assertThat(ampEvent).isEqualTo(AmpEvent.builder()
                .context(routingContext)
                .uidsCookie(uidsCookie)
                .origin("http://example.com")
                .status(400)
                .errors(singletonList("Request is invalid"))
                .build());
    }

    @Test
    public void shouldPassInternalServerErrorEventToAnalyticsReporterIfAuctionFails() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(emptyList()).build();
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(bidRequest));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        ampHandler.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        assertThat(ampEvent).isEqualTo(AmpEvent.builder()
                .context(routingContext)
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .origin("http://example.com")
                .status(500)
                .errors(singletonList("Unexpected exception"))
                .build());
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(emptyList()).build();
        given(ampRequestFactory.fromRequest(any()))
                .willReturn(Future.succeededFuture(bidRequest));

        given(exchangeService.holdAuction(any(), any(), any(), any(), any()))
                .willReturn(givenBidResponse(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, singletonMap("hb_cache_id_bidder1", "value1"), null),
                                null))));

        // when
        ampHandler.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        assertThat(ampEvent).isEqualTo(AmpEvent.builder()
                .context(routingContext)
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .bidResponse(BidResponse.builder().seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(
                                        ExtBidPrebid.of(null, singletonMap("hb_cache_id_bidder1", "value1"), null),
                                        null)))
                                .build()))
                        .build()))
                        .build())
                .targeting(singletonMap("hb_cache_id_bidder1", TextNode.valueOf("value1")))
                .origin("http://example.com")
                .status(200)
                .errors(emptyList())
                .build());
    }

    private static Future<BidResponse> givenBidResponse(ObjectNode extBid) {
        return Future.succeededFuture(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .ext(extBid)
                                .build()))
                        .build()))
                .build());
    }

    private static Future<BidResponse> givenBidResponseWithExt(ObjectNode extBidResponse) {
        return Future.succeededFuture(BidResponse.builder()
                .ext(extBidResponse)
                .build());
    }

    private AmpEvent captureAmpEvent() {
        final ArgumentCaptor<AmpEvent> ampEventCaptor = ArgumentCaptor.forClass(AmpEvent.class);
        verify(analyticsReporter).processEvent(ampEventCaptor.capture());
        return ampEventCaptor.getValue();
    }
}
