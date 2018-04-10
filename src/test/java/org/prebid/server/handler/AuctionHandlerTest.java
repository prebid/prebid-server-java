package org.prebid.server.handler;

import com.codahale.metrics.MetricRegistry;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Format;
import io.netty.util.AsciiString;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.AdapterResponse;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.auction.model.PreBidRequestContext.PreBidRequestContextBuilder;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.proto.BidCacheResult;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.AccountMetrics;
import org.prebid.server.metric.AdapterMetrics;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.prebid.AuctionHandlerMetrics;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.request.PreBidRequest.PreBidRequestBuilder;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.BidderStatus.BidderStatusBuilder;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.proto.response.PreBidResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.*;

public class AuctionHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private Adapter<?, ?> rubiconAdapter;
    @Mock
    private Adapter<?, ?> appnexusAdapter;
    @Mock
    private PreBidRequestContextFactory preBidRequestContextFactory;
    @Mock
    private CacheService cacheService;
    @Mock
    private org.prebid.server.metric.Metrics metrics;
    @Mock
    private HttpAdapterConnector httpAdapterConnector;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    private Clock clock;
    private AuctionHandler auctionHandler;
    private static MetricRegistry registry = new MetricRegistry();
    private AuctionHandlerMetrics handlerMetrics;
    private AdapterMetrics adapterMetrics;
    private AccountMetrics accountMetrics;
    private AdapterMetrics accountAdapterMetrics;

    @Before
    public void setUp() {
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.of(null, null)));

        given(bidderCatalog.isValidAdapterName(eq(RUBICON))).willReturn(true);
        given(bidderCatalog.isValidName(eq(RUBICON))).willReturn(true);
        given(bidderCatalog.isActive(eq(RUBICON))).willReturn(true);
        willReturn(rubiconAdapter).given(bidderCatalog).adapterByName(eq(RUBICON));

        given(bidderCatalog.isValidAdapterName(eq(APPNEXUS))).willReturn(true);
        given(bidderCatalog.isValidName(eq(APPNEXUS))).willReturn(true);
        given(bidderCatalog.isActive(eq(APPNEXUS))).willReturn(true);
        willReturn(appnexusAdapter).given(bidderCatalog).adapterByName(eq(APPNEXUS));

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        adapterMetrics = spy(new org.prebid.server.metric.dropwizard.AdapterMetrics(registry, CounterType.flushingCounter, "rubicon"));
        accountMetrics = spy(new org.prebid.server.metric.dropwizard.AccountMetrics(registry, CounterType.flushingCounter, "rubicon"));
        accountAdapterMetrics = spy(new org.prebid.server.metric.dropwizard.AdapterMetrics(registry, CounterType.flushingCounter, "rubicon"));

        given(metrics.forAdapter(any())).willReturn(adapterMetrics);
        given(metrics.forAccount(anyString())).willReturn(accountMetrics);
        given(accountMetrics.forAdapter(anyString())).willReturn(accountAdapterMetrics);

        handlerMetrics = spy(new AuctionHandlerMetrics(metrics, clock));

        auctionHandler = new AuctionHandler(applicationSettings, bidderCatalog, preBidRequestContextFactory,
                cacheService, handlerMetrics, httpAdapterConnector, clock);
    }

    @Test
    public void shouldRespondWithErrorIfRequestIsNotValid() throws IOException {
        // given
        given(preBidRequestContextFactory.fromRequest(any())).willReturn(Future.failedFuture(new PreBidException("Could not create")));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("Error parsing request: Could not create");
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyHasUnknownAccountId() throws IOException {
        // given
        givenPreBidRequestContext(identity(), identity());

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("Unknown account id: Unknown account");
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        givenPreBidRequestContext(identity(), identity());

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).putHeader(eq(new AsciiString("Date")), ArgumentMatchers.<CharSequence>isNotNull());
        verify(httpResponse)
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfNoLiveUidsInCookie() throws IOException {
        // given
        final boolean hasLiveUids = true;
        givenPreBidRequestContext(identity(), builder -> builder.noLiveUids(hasLiveUids));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("no_cookie");
    }

    @Test
    public void shouldRespondWithErrorIfUnexpectedExceptionOccurs() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("Unexpected server error");
    }

    @Test
    public void shouldInteractWithCacheServiceIfRequestHasBidsAndCacheMarkupFlag() throws IOException {
        // given
        final Timeout timeout = new TimeoutFactory(clock).create(500L);

        givenPreBidRequestContext(
                builder -> builder.cacheMarkup(1),
                builder -> builder
                        .timeout(timeout)
                        .adapterRequests(singletonList(AdapterRequest.of(RUBICON, singletonList(null)))));

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        given(cacheService.cacheBids(anyList(), any())).willReturn(Future.succeededFuture(singletonList(
                BidCacheResult.of("0b4f60d1-fb99-4d95-ba6f-30ac90f9a315", "cached_asset_url"))));
        given(cacheService.getCachedAssetURL(anyString())).willReturn("cached_asset_url");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(cacheService).cacheBids(anyList(), same(timeout));
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).extracting(Bid::getAdm).containsNull();
        assertThat(preBidResponse.getBids()).extracting(Bid::getNurl).containsNull();
        assertThat(preBidResponse.getBids()).extracting(Bid::getCacheId)
                .containsOnly("0b4f60d1-fb99-4d95-ba6f-30ac90f9a315");
        assertThat(preBidResponse.getBids()).extracting(Bid::getCacheUrl).containsOnly("cached_asset_url");
    }

    @Test
    public void shouldNotInteractWithCacheServiceIfRequestHasBidsAndNoCacheMarkupFlag() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        verifyZeroInteractions(cacheService);
        assertThat(preBidResponse.getBids()).extracting(Bid::getCacheId).containsNull();
        assertThat(preBidResponse.getBids()).extracting(Bid::getCacheUrl).containsNull();
    }

    @Test
    public void shouldNotInteractWithCacheServiceIfRequestHasNoBidsButCacheMarkupFlag() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(builder -> builder.cacheMarkup(1));

        givenBidderRespondingWithBids(RUBICON, identity());

        // when
        auctionHandler.handle(routingContext);

        // then
        verifyZeroInteractions(cacheService);
    }

    @Test
    public void shouldRespondWithErrorIfCacheServiceFails() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(builder -> builder.cacheMarkup(1));

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        given(cacheService.cacheBids(anyList(), any())).willReturn(Future.failedFuture("http exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("Prebid cache failed: http exception");
    }

    @Test
    public void shouldRespondWithMultipleBidderStatusesAndBidsWhenMultipleAdUnitsAndBidsInPreBidRequest()
            throws IOException {
        // given
        givenPreBidRequestContextWith2AdUnitsAnd2BidsEach(builder -> builder.noLiveUids(false));

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(RUBICON).responseTimeMs(100).build(),
                        Arrays.stream(new String[]{"bidId1", "bidId2"})
                                .map(id -> org.prebid.server.proto.response.Bid.builder()
                                        .bidId(id)
                                        .price(new BigDecimal("5.67"))
                                        .build())
                                .collect(Collectors.toList()),
                        false)))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(APPNEXUS).responseTimeMs(100).build(),
                        Arrays.stream(new String[]{"bidId3", "bidId4"})
                                .map(id -> org.prebid.server.proto.response.Bid.builder()
                                        .bidId(id)
                                        .price(new BigDecimal("5.67"))
                                        .build())
                                .collect(Collectors.toList()),
                        false)));
        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getStatus()).isEqualTo("OK");
        assertThat(preBidResponse.getTid()).isEqualTo("tid");
        assertThat(preBidResponse.getBidderStatus()).extracting(BidderStatus::getBidder)
                .containsOnly(RUBICON, APPNEXUS);
        assertThat(preBidResponse.getBids()).extracting(Bid::getBidId)
                .containsOnly("bidId1", "bidId2", "bidId3", "bidId4");
    }

    @Test
    public void shouldRespondWithBidsWithTargetingKeywordsWhenSortBidsFlagIsSetInPreBidRequest() throws IOException {
        // given
        final List<AdUnitBid> adUnitBids = asList(null, null);
        final List<AdapterRequest> adapterRequests = asList(AdapterRequest.of(RUBICON, adUnitBids),
                AdapterRequest.of(APPNEXUS, adUnitBids));

        givenPreBidRequestContext(builder -> builder.sortBids(1), builder -> builder.adapterRequests(adapterRequests));

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(RUBICON).responseTimeMs(100).build(),
                        asList(
                                org.prebid.server.proto.response.Bid.builder()
                                        .bidder(RUBICON).code("adUnitCode1").bidId("bidId1")
                                        .price(new BigDecimal("5.67"))
                                        .responseTimeMs(60).adServerTargeting(
                                        singletonMap("rpfl_1001", "2_tier0100")).build(),
                                org.prebid.server.proto.response.Bid.builder()
                                        .bidder(RUBICON).code("adUnitCode2").bidId("bidId2")
                                        .price(new BigDecimal("6.35"))
                                        .responseTimeMs(80).build()),
                        false)))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(APPNEXUS).responseTimeMs(100).build(),
                        asList(
                                org.prebid.server.proto.response.Bid.builder()
                                        .bidder(APPNEXUS).code("adUnitCode1").bidId("bidId3")
                                        .price(new BigDecimal("5.67"))
                                        .responseTimeMs(50).build(),
                                org.prebid.server.proto.response.Bid.builder()
                                        .bidder(APPNEXUS).code("adUnitCode2").bidId("bidId4")
                                        .price(new BigDecimal("7.15"))
                                        .responseTimeMs(100).build()),
                        false)));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).extracting(Bid::getAdServerTargeting).doesNotContainNull();
        // verify that ad server targeting has been preserved
        assertThat(preBidResponse.getBids()).extracting(Bid::getBidId, b -> b.getAdServerTargeting().get("rpfl_1001"))
                .contains(tuple("bidId1", "2_tier0100"));
        // weird way to verify that sorting has happened before bids grouped by ad unit code are enriched with targeting
        // keywords
        assertThat(preBidResponse.getBids()).extracting(Bid::getBidId, b -> b.getAdServerTargeting().get("hb_bidder"))
                .containsOnly(
                        tuple("bidId1", null),
                        tuple("bidId2", null),
                        tuple("bidId3", APPNEXUS),
                        tuple("bidId4", APPNEXUS));
    }

    @Test
    public void shouldRespondWithValidBannerBidIfSizeIsMissedButRecoveredFromAdUnit() throws IOException {
        // given
        final List<AdUnitBid> adUnitBids = singletonList(AdUnitBid.builder()
                .adUnitCode("adUnitCode1")
                .bidId("bidId1")
                .sizes(singletonList(Format.builder().w(100).h(200).build()))
                .build());
        final List<AdapterRequest> adapterRequests = singletonList(AdapterRequest.of(RUBICON, adUnitBids));

        givenPreBidRequestContext(identity(), builder -> builder.adapterRequests(adapterRequests));

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(RUBICON).responseTimeMs(100).numBids(1).build(),
                        singletonList(
                                org.prebid.server.proto.response.Bid.builder().mediaType(MediaType.banner)
                                        .bidder(RUBICON).code("adUnitCode1").bidId("bidId1")
                                        .price(new BigDecimal("5.67")).build()),
                        false)));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).hasSize(1);
        assertThat(preBidResponse.getBidderStatus().get(0).getNumBids()).isEqualTo(1);
    }

    @Test
    public void shouldRespondWithValidVideoBidEvenIfSizeIsMissed() throws IOException {
        // given
        final List<AdUnitBid> adUnitBids = singletonList(null);
        final List<AdapterRequest> adapterRequests = singletonList(AdapterRequest.of(RUBICON, adUnitBids));

        givenPreBidRequestContext(identity(), builder -> builder.adapterRequests(adapterRequests));

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(RUBICON).responseTimeMs(100).numBids(1).build(),
                        singletonList(
                                org.prebid.server.proto.response.Bid.builder().mediaType(MediaType.video)
                                        .bidId("bidId1").price(new BigDecimal("5.67")).build()),
                        false)));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBids()).hasSize(1);
        assertThat(preBidResponse.getBidderStatus().get(0).getNumBids()).isEqualTo(1);
    }

    @Test
    public void shouldTolerateUnsupportedBidderInPreBidRequest() throws IOException {
        // given
        final List<AdapterRequest> adapterRequests = asList(
                AdapterRequest.of("unsupported", singletonList(null)),
                AdapterRequest.of(RUBICON, singletonList(null)));

        givenPreBidRequestContext(identity(), builder -> builder.adapterRequests(adapterRequests));

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBidderStatus())
                .extracting(BidderStatus::getBidder, BidderStatus::getError).containsOnly(
                tuple("unsupported", "Unsupported bidder"),
                tuple(RUBICON, null));
        assertThat(preBidResponse.getBids()).hasSize(1);
    }

    @Test
    public void shouldTolerateErrorResultFromAdapter() throws IOException {
        // given
        givenPreBidRequestContextWith2AdUnitsAnd2BidsEach(identity());

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(RUBICON).responseTimeMs(500).error("rubicon error").build(),
                        emptyList(), false)))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(APPNEXUS).responseTimeMs(100).build(),
                        singletonList(org.prebid.server.proto.response.Bid.builder()
                                .bidId("bidId1")
                                .price(new BigDecimal("5.67"))
                                .build()),
                        false)));

        // when
        auctionHandler.handle(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.getBidderStatus()).extracting(BidderStatus::getBidder, BidderStatus::getError)
                .containsOnly(
                        tuple(RUBICON, "rubicon error"),
                        tuple(APPNEXUS, null));
        assertThat(preBidResponse.getBids()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncrementCommonMetrics() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(builder -> builder.app(App.builder().build()));

        // simulate calling end handler that is supposed to update request_time timer value
        given(httpResponse.endHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        givenBidderRespondingWithBids(RUBICON, builder -> builder.noCookie(true).numBids(1), "bidId1");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.requests));
        verify(metrics).incCounter(eq(MetricName.app_requests));
        verify(accountMetrics).incCounter(eq(MetricName.requests));
        verify(metrics).updateTimer(eq(MetricName.request_time), anyLong());
        verify(adapterMetrics).incCounter(eq(MetricName.requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.requests));
        verify(adapterMetrics).updateTimer(eq(MetricName.request_time), eq(100L));
        verify(accountAdapterMetrics).updateTimer(eq(MetricName.request_time), eq(100L));
        verify(adapterMetrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(accountMetrics).incCounter(eq(MetricName.bids_received), eq(1L));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.bids_received), eq(1L));
        verify(adapterMetrics).updateHistogram(eq(MetricName.prices), eq(5670L));
        verify(accountMetrics).updateHistogram(eq(MetricName.prices), eq(5670L));
        verify(accountAdapterMetrics).updateHistogram(eq(MetricName.prices), eq(5670L));
        verify(accountMetrics, never()).incCounter(eq(MetricName.no_bid_requests));
        verify(accountAdapterMetrics, never()).incCounter(eq(MetricName.no_bid_requests));
        verify(metrics, never()).incCounter(eq(MetricName.safari_requests));
        verify(metrics, never()).incCounter(eq(MetricName.no_cookie_requests));
        verify(metrics, never()).incCounter(eq(MetricName.safari_no_cookie_requests));
        verify(metrics, never()).incCounter(eq(MetricName.error_requests));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncrementNoBidMetrics() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        givenBidderRespondingWithBids(RUBICON, builder -> builder.noBid(true));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.no_bid_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.no_bid_requests));
    }

    @Test
    public void shouldIncrementSafariAndNoCookieMetrics() {
        // given
        givenPreBidRequestContext(identity(), builder -> builder.noLiveUids(true));

        httpRequest.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
                "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.safari_requests));
        verify(metrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(metrics).incCounter(eq(MetricName.safari_no_cookie_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfRequestBodyHasUnknownAccountId() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(handlerMetrics).bidResponseOrError(any());
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfRequestIsNotValid() {
        // given
        PreBidException ex = new PreBidException("Could not create");
        given(preBidRequestContextFactory.fromRequest(any())).willReturn(Future.failedFuture(ex));


        // when
        auctionHandler.handle(routingContext);

        // then
        verify(handlerMetrics).bidResponseOrError(any());
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfAdapterReturnsError() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        givenBidderRespondingWithError(RUBICON, "rubicon error", false);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.error_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfAdapterReturnsTimeoutError() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(identity());

        givenBidderRespondingWithError(RUBICON, "time out", true);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(adapterMetrics).incCounter(eq(MetricName.timeout_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.timeout_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfCacheServiceFails() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1Bid(builder -> builder.cacheMarkup(1));

        givenBidderRespondingWithBids(RUBICON, identity(), "bidId1");

        given(cacheService.cacheBids(anyList(), any())).willReturn(Future.failedFuture("http exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(handlerMetrics).bidResponseOrError(any());
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    private PreBidRequestContext givenPreBidRequestContextWith1AdUnitAnd1Bid(
            Function<PreBidRequestBuilder, PreBidRequestBuilder> preBidRequestBuilderCustomizer) {

        final List<AdapterRequest> adapterRequests = singletonList(AdapterRequest.of(RUBICON, singletonList(null)));
        return givenPreBidRequestContext(preBidRequestBuilderCustomizer, builder -> builder.adapterRequests(adapterRequests));
    }

    private PreBidRequestContext givenPreBidRequestContextWith2AdUnitsAnd2BidsEach(
            Function<PreBidRequestContextBuilder, PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer) {
        final List<AdUnitBid> adUnitBids = asList(null, null);
        final List<AdapterRequest> adapterRequests = asList(
                AdapterRequest.of(RUBICON, adUnitBids),
                AdapterRequest.of(APPNEXUS, adUnitBids));
        return givenPreBidRequestContext(identity(),
                preBidRequestContextBuilderCustomizer.compose(builder -> builder.adapterRequests(adapterRequests)));
    }

    private PreBidRequestContext givenPreBidRequestContext(
            Function<PreBidRequestBuilder, PreBidRequestBuilder> preBidRequestBuilderCustomizer,
            Function<PreBidRequestContextBuilder, PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer) {

        final PreBidRequest preBidRequest = preBidRequestBuilderCustomizer.apply(
                PreBidRequest.builder()
                        .tid("tid")
                        .accountId("accountId")
                        .adUnits(emptyList()))
                .build();
        final PreBidRequestContext preBidRequestContext = preBidRequestContextBuilderCustomizer.apply(
                PreBidRequestContext.builder()
                        .adapterRequests(emptyList())
                        .preBidRequest(preBidRequest))
                .build();
        given(preBidRequestContextFactory.fromRequest(any())).willReturn(Future.succeededFuture(preBidRequestContext));
        return preBidRequestContext;
    }

    private void givenBidderRespondingWithBids(String bidder, Function<BidderStatusBuilder, BidderStatusBuilder>
            bidderStatusBuilderCustomizer, String... bidIds) {
        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        bidderStatusBuilderCustomizer.apply(BidderStatus.builder()
                                .bidder(bidder)
                                .responseTimeMs(100))
                                .build(),
                        Arrays.stream(bidIds)
                                .map(id -> org.prebid.server.proto.response.Bid.builder()
                                        .bidId(id)
                                        .price(new BigDecimal("5.67"))
                                        .build())
                                .collect(Collectors.toList()),
                        false)));
    }

    private void givenBidderRespondingWithError(String bidder, String error, boolean timedOut) {
        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().bidder(bidder).responseTimeMs(500).error(error).build(),
                        emptyList(), timedOut)));
    }

    private PreBidResponse capturePreBidResponse() throws IOException {
        final ArgumentCaptor<String> preBidResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(preBidResponseCaptor.capture());
        return mapper.readValue(preBidResponseCaptor.getValue(), PreBidResponse.class);
    }
}
