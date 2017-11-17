package org.rtb.vexing.handler;

import com.iab.openrtb.request.App;
import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.cache.model.response.BidCacheResponse;
import org.rtb.vexing.cache.model.response.Response;
import org.rtb.vexing.metric.AccountMetrics;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.request.AdUnit;
import org.rtb.vexing.model.request.Bid;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.PreBidResponse;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.settings.model.Account;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class AuctionHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private AdapterCatalog adapterCatalog;
    @Mock
    private Adapter rubiconAdapter;
    @Mock
    private Adapter appnexusAdapter;
    @Mock
    private CacheService cacheService;
    @Mock
    private Vertx vertx;
    @Mock
    private Metrics metrics;
    @Mock
    private AccountMetrics accountMetrics;
    private AuctionHandler auctionHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(applicationSettings.getAccountById(any())).willReturn(Optional.of(Account.builder().build()));

        given(adapterCatalog.get(eq(RUBICON))).willReturn(rubiconAdapter);
        given(adapterCatalog.get(eq(APPNEXUS))).willReturn(appnexusAdapter);

        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(AdditionalAnswers.<Long, Handler<Long>>answerVoid((p, h) -> h.handle(0L)));

        given(metrics.forAccount(anyString())).willReturn(accountMetrics);

        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);

        auctionHandler = new AuctionHandler(applicationSettings, adapterCatalog, cacheService, vertx, metrics);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, cacheService, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, cacheService, vertx, null));
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(null);

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(httpResponse, times(1)).setStatusCode(eq(400));
        verify(httpResponse, times(1)).end();
        verifyNoMoreInteractions(httpResponse, adapterCatalog);
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyHasUnknownAccountId() throws IOException {
        // given
        givenPreBidRequestWith1AdUnitAnd1Bid(identity());

        given(applicationSettings.getAccountById(any())).willReturn(Optional.empty());

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.status).isEqualTo("Unknown account id");
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        givenPreBidRequestCustomizable(identity());

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(httpResponse, times(1))
                .putHeader(eq(new AsciiString("Date")), ArgumentMatchers.<CharSequence>isNotNull());
        verify(httpResponse, times(1))
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithNoBidIfAtLeastOneAdapterFailed() throws IOException {
        // given
        givenPreBidRequestWith2AdUnitsAnd2BidsEach();

        given(rubiconAdapter.requestBids(any(), any(), any(), any())).willReturn(Future.failedFuture("failure"));
        givenAdapterRespondingWithBids(appnexusAdapter, null);

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse).isEqualTo(PreBidResponse.builder().build());
    }

    @Test
    public void shouldInteractWithCacheServiceIfRequestHasBidsAndCacheMarkupFlag() throws IOException {
        // given
        givenPreBidRequestWith1AdUnitAnd1Bid(builder -> builder.cacheMarkup(1));
        givenAdapterRespondingWithBids(rubiconAdapter, RUBICON, "bidId1");

        given(cacheService.saveBids(anyList())).willReturn(Future.succeededFuture(BidCacheResponse.builder()
                .responses(singletonList(Response.builder()
                        .uuid("0b4f60d1-fb99-4d95-ba6f-30ac90f9a315")
                        .build()))
                .build()));

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(cacheService).saveBids(anyList());

        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.bids).extracting(b -> b.adm).containsNull();
        assertThat(preBidResponse.bids).extracting(b -> b.nurl).containsNull();
        assertThat(preBidResponse.bids).extracting(b -> b.cacheId).containsOnly("0b4f60d1-fb99-4d95-ba6f-30ac90f9a315");
    }

    @Test
    public void shouldNotInteractWithCacheServiceIfRequestHasBidsAndNoCacheMarkupFlag() throws IOException {
        // given
        givenPreBidRequestWith1AdUnitAnd1Bid(identity());
        givenAdapterRespondingWithBids(rubiconAdapter, RUBICON, "bidId1");

        // when
        auctionHandler.auction(routingContext);

        // then
        verifyZeroInteractions(cacheService);

        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.bids).extracting(b -> b.cacheId).containsNull();
    }

    @Test
    public void shouldNotInteractWithCacheServiceIfRequestHasNoBidsButCacheMarkupFlag() throws IOException {
        // given
        givenPreBidRequestWith1AdUnitAnd1Bid(builder -> builder.cacheMarkup(1));
        givenAdapterRespondingWithBids(rubiconAdapter, RUBICON);

        // when
        auctionHandler.auction(routingContext);

        // then
        verifyZeroInteractions(cacheService);
    }

    @Test
    public void shouldRespondWithMultipleBidderStatusesAndBidsWhenMultipleAdUnitsAndBidsInPreBidRequest()
            throws IOException {
        // given
        givenPreBidRequestWith2AdUnitsAnd2BidsEach();

        givenAdapterRespondingWithBids(rubiconAdapter, RUBICON, "bidId1", "bidId2");
        givenAdapterRespondingWithBids(appnexusAdapter, APPNEXUS, "bidId3", "bidId4");

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.status).isEqualTo("OK");
        assertThat(preBidResponse.tid).isEqualTo("tid");
        assertThat(preBidResponse.bidderStatus).extracting(b -> b.bidder).containsOnly(RUBICON, APPNEXUS);
        assertThat(preBidResponse.bids).extracting(b -> b.bidId).containsOnly("bidId1", "bidId2", "bidId3", "bidId4");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncrementCommonMetrics() {
        // given
        given(routingContext.getBodyAsJson())
                .willReturn(JsonObject.mapFrom(PreBidRequest.builder()
                        .accountId("accountId")
                        .adUnits(emptyList())
                        .app(App.builder().build())
                        .build()));

        // simulate calling end handler that is supposed to update request_time timer value
        given(httpResponse.endHandler(any())).willAnswer(inv -> {
            ((Handler<Void>) inv.getArgument(0)).handle(null);
            return null;
        });

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.requests));
        verify(metrics).incCounter(eq(MetricName.app_requests));
        verify(accountMetrics).incCounter(eq(MetricName.requests));
        verify(metrics).updateTimer(eq(MetricName.request_time), anyLong());
    }

    @Test
    public void shouldIncrementSafariAndNoCookieMetrics() {
        // given
        givenPreBidRequestCustomizable(identity());

        httpRequest.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
                "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.safari_requests));
        verify(metrics).incCounter(eq(MetricName.no_cookie_requests));
        verify(metrics).incCounter(eq(MetricName.safari_no_cookie_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfRequestBodyHasUnknownAccountId() {
        // given
        givenPreBidRequestWith1AdUnitAnd1Bid(identity());

        given(applicationSettings.getAccountById(any())).willReturn(Optional.empty());

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    private void givenPreBidRequestWith1AdUnitAnd1Bid(
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder>
                    preBidRequestBuilderCustomizer) {

        final AdUnit adUnit = AdUnit.builder()
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .build();
        Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder>
                minimalCustomizer = builder -> builder.adUnits(singletonList(adUnit));

        givenPreBidRequestCustomizable(preBidRequestBuilderCustomizer.compose(minimalCustomizer));
    }

    private void givenPreBidRequestWith2AdUnitsAnd2BidsEach() {
        final AdUnit adUnit = AdUnit.builder()
                .bids(asList(Bid.builder().bidder(RUBICON).build(), Bid.builder().bidder(APPNEXUS).build()))
                .build();
        givenPreBidRequestCustomizable(builder -> builder.adUnits(asList(adUnit, adUnit)));
    }

    private void givenPreBidRequestCustomizable(
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder>
                    preBidRequestBuilderCustomizer) {
        final PreBidRequest.PreBidRequestBuilder preBidRequestBuilderMinimal = PreBidRequest.builder()
                .tid("tid")
                .accountId("accountId")
                .timeoutMillis(1000L)
                .adUnits(emptyList());

        final PreBidRequest preBidRequest = preBidRequestBuilderCustomizer.apply(preBidRequestBuilderMinimal).build();
        given(routingContext.getBodyAsJson()).willReturn(JsonObject.mapFrom(preBidRequest));
    }

    private void givenAdapterRespondingWithBids(Adapter adapter, String bidder, String... bidIds) {
        List<org.rtb.vexing.model.response.Bid> bids = Arrays.stream(bidIds)
                .map(id -> org.rtb.vexing.model.response.Bid.builder().bidId(id).build())
                .collect(Collectors.toList());

        given(adapter.requestBids(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.builder()
                        .bidderStatus(BidderStatus.builder().bidder(bidder).build())
                        .bids(bids)
                        .build()));
    }

    private PreBidResponse capturePreBidResponse() throws IOException {
        final ArgumentCaptor<String> preBidResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse, times(1)).end(preBidResponseCaptor.capture());
        return mapper.readValue(preBidResponseCaptor.getValue(), PreBidResponse.class);
    }
}
