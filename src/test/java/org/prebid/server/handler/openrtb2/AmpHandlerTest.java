package org.prebid.server.handler.openrtb2;

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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.auction.requestfactory.AmpRequestFactory;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
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
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class AmpHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

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

    private AmpHandler ampHandler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private UidsCookie uidsCookie;
    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    private Timeout timeout;

    @Before
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

        timeout = new TimeoutFactory(clock).create(2000L);

        ampHandler = new AmpHandler(
                ampRequestFactory,
                exchangeService,
                analyticsReporterDelegator,
                metrics,
                clock,
                bidderCatalog,
                singleton("bidder1"),
                new AmpResponsePostProcessor.NoOpAmpResponsePostProcessor(),
                httpInteractionLogger,
                prebidVersionProvider,
                jacksonMapper
        );
    }

    @Test
    public void shouldSetRequestTypeMetricToAuctionContext() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // when
        ampHandler.handle(routingContext);

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
        ampHandler.handle(routingContext);

        // then
        assertThat(captureAuctionContext().getTimeout().remaining()).isEqualTo(2000L);
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
        ampHandler.handle(routingContext);

        // then
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("x-prebid", "pbs-java/1.00"));
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
        ampHandler.handle(routingContext);

        // then
        assertThat(captureAuctionContext().getTimeout().remaining()).isLessThanOrEqualTo(1950L);
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestIsInvalid() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        ampHandler.handle(routingContext);

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
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestHasBlacklistedAccount() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new BlacklistedAccountException("Blacklisted account")));

        // when
        ampHandler.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(403));
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Blacklisted: Blacklisted account"));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestHasBlacklistedApp() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new BlacklistedAppException("Blacklisted app")));

        // when
        ampHandler.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(403));
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Blacklisted: Blacklisted app"));
    }

    @Test
    public void shouldRespondWithUnauthorizedIfAccountIdIsInvalid() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new UnauthorizedAccountException("Account id is not provided", null)));

        // when
        ampHandler.handle(routingContext);

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
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
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
        ampHandler.handle(routingContext);

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
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).end(anyString());
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
        ampHandler.handle(routingContext);

        // then
        assertThat(httpResponse.headers()).hasSize(4)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("{\"targeting\":{\"key1\":\"value1\",\"hb_cache_id_bidder1\":\"value2\"}}"));
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
        ampHandler.handle(routingContext);

        // then
        assertThat(httpResponse.headers()).hasSize(4)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("AMP-Access-Control-Allow-Source-Origin", "http://example.com"),
                        tuple("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin"),
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("{\"targeting\":{\"key1\":\"value1\",\"rpfl_11078\":\"15_tier0030\","
                + "\"hb_cache_id_bidder1\":\"value2\"}}"));
    }

    @Test
    public void shouldRespondWithDebugInfoIncludedIfTestFlagIsTrue() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(builder -> builder.id("reqId1")).toBuilder()
                .debugContext(DebugContext.of(true, null))
                .build();
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        givenHoldAuction(givenBidResponseWithExt(
                ExtBidResponse.builder()
                        .debug(ExtResponseDebug.of(null, auctionContext.getBidRequest(), null, null))
                        .prebid(ExtBidResponsePrebid.of(1000L, null))
                        .build()));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq(
                "{\"targeting\":{},"
                        + "\"ext\":{\"debug\":{\"resolvedrequest\":{\"id\":\"reqId1\",\"imp\":[],\"tmax\":5000}}}}"));
    }

    @Test
    public void shouldRespondWithHooksDebugAndTraceOutput() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity());
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        givenHoldAuction(givenBidResponseWithExt(
                ExtBidResponse.builder()
                        .prebid(ExtBidResponsePrebid.of(
                                1000L,
                                ExtModules.of(
                                        singletonMap(
                                                "module1", singletonMap("hook1", singletonList("error1"))),
                                        singletonMap(
                                                "module1", singletonMap("hook1", singletonList("warning1"))),
                                        ExtModulesTrace.of(2L, emptyList()))))
                        .build()));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq(
                "{\"targeting\":{},"
                        + "\"ext\":{\"prebid\":{\"modules\":{"
                        + "\"errors\":{\"module1\":{\"hook1\":[\"error1\"]}},"
                        + "\"warnings\":{\"module1\":{\"hook1\":[\"warning1\"]}},"
                        + "\"trace\":{\"executiontimemillis\":2,\"stages\":[]}}}}}"));
    }

    @Test
    public void shouldIncrementOkAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(givenBidResponse(mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().build(), null))));

        // when
        ampHandler.handle(routingContext);

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
        ampHandler.handle(routingContext);

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
        ampHandler.handle(routingContext);

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
        ampHandler.handle(routingContext);

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
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateImpTypesMetrics(same(imps));
    }

    @Test
    public void shouldIncrementBadinputAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.badinput));
    }

    @Test
    public void shouldIncrementErrAmpRequestMetrics() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

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

        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity())));

        givenHoldAuction(BidResponse.builder().build());

        // simulate calling end handler that is supposed to update request_time timer value
        given(httpResponse.endHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTimeMetric(eq(MetricName.request_time), eq(500L));
    }

    @Test
    public void shouldNotUpdateRequestTimeMetricIfRequestFails() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
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
        ampHandler.handle(routingContext);

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
        ampHandler.handle(routingContext);

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
        ampHandler.handle(routingContext);

        // then
        verify(metrics).updateRequestTypeMetric(eq(MetricName.amp), eq(MetricName.networkerr));
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfBidRequestIsInvalid() {
        // given
        given(ampRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        ampHandler.handle(routingContext);

        // then
        final AmpEvent ampEvent = captureAmpEvent();
        assertThat(ampEvent).isEqualTo(AmpEvent.builder()
                .httpContext(givenHttpContext(singletonMap("Origin", "http://example.com")))
                .origin("http://example.com")
                .status(400)
                .errors(singletonList("Invalid request format: Request is invalid"))
                .build());
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
        ampHandler.handle(routingContext);

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
        ampHandler.handle(routingContext);

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
        final AuctionContext expectedAuctionContext = auctionContext.toBuilder()
                .requestTypeMetric(MetricName.amp)
                .bidResponse(expectedBidResponse)
                .build();

        assertThat(ampEvent).isEqualTo(AmpEvent.builder()
                .httpContext(givenHttpContext(singletonMap("Origin", "http://example.com")))
                .auctionContext(expectedAuctionContext)
                .bidResponse(expectedBidResponse)
                .targeting(singletonMap("hb_cache_id_bidder1", TextNode.valueOf("value1")))
                .origin("http://example.com")
                .status(200)
                .errors(emptyList())
                .build());
    }

    private AuctionContext givenAuctionContext(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(BidRequest.builder()
                .imp(emptyList()).tmax(5000L)).build();

        return AuctionContext.builder()
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .requestTypeMetric(MetricName.amp)
                .timeout(timeout)
                .debugContext(DebugContext.empty())
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
