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
import org.rtb.vexing.adapter.PreBidRequestContextFactory;
import org.rtb.vexing.adapter.PreBidRequestException;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.cache.model.BidCacheResult;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.metric.AccountMetrics;
import org.rtb.vexing.metric.AdapterMetrics;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.PreBidRequestContext;
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
import static org.assertj.core.api.Assertions.*;
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
    private PreBidRequestContextFactory preBidRequestContextFactory;
    @Mock
    private CacheService cacheService;
    @Mock
    private Vertx vertx;
    @Mock
    private Metrics metrics;
    @Mock
    private AdapterMetrics adapterMetrics;
    @Mock
    private AccountMetrics accountMetrics;
    @Mock
    private AdapterMetrics accountAdapterMetrics;

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

        given(metrics.forAdapter(any())).willReturn(adapterMetrics);
        given(metrics.forAccount(anyString())).willReturn(accountMetrics);
        given(accountMetrics.forAdapter(any())).willReturn(accountAdapterMetrics);

        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);

        auctionHandler = new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory,
                cacheService, vertx, metrics);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory, null,
                        null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory,
                        cacheService, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory,
                        cacheService, vertx, null));
    }

    @Test
    public void shouldRespondWithErrorIfRequestIsNotValid() throws IOException {
        // given
        given(preBidRequestContextFactory.fromRequest(any())).willThrow(new PreBidRequestException("Could not create"));

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.status).isEqualTo("Error parsing request: Could not create");
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyHasUnknownAccountId() throws IOException {
        // given
        givenPreBidRequestContext();

        given(applicationSettings.getAccountById(any())).willReturn(Optional.empty());

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.status).isEqualTo("Unknown account id: Unknown account");
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        givenPreBidRequestContext();

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(httpResponse, times(1))
                .putHeader(eq(new AsciiString("Date")), ArgumentMatchers.<CharSequence>isNotNull());
        verify(httpResponse, times(1))
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithErrorIfUnexpectedExceptionOccurs() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1BidCustomizable(identity());

        given(rubiconAdapter.requestBids(any(), any())).willReturn(Future.failedFuture(new RuntimeException()));

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.status).isEqualTo("Unexpected server error");
    }

    @Test
    public void shouldInteractWithCacheServiceIfRequestHasBidsAndCacheMarkupFlag() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1BidCustomizable(builder -> builder.cacheMarkup(1));

        givenAdapterRespondingWithBids(rubiconAdapter, RUBICON, "bidId1");

        given(cacheService.saveBids(anyList())).willReturn(Future.succeededFuture(singletonList(BidCacheResult
                .builder()
                .cacheId("0b4f60d1-fb99-4d95-ba6f-30ac90f9a315")
                .cacheUrl("cached_asset_url")
                .build())));
        given(cacheService.getCachedAssetURL(anyString())).willReturn("cached_asset_url");

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(cacheService).saveBids(anyList());

        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.bids).extracting(b -> b.adm).containsNull();
        assertThat(preBidResponse.bids).extracting(b -> b.nurl).containsNull();
        assertThat(preBidResponse.bids).extracting(b -> b.cacheId).containsOnly("0b4f60d1-fb99-4d95-ba6f-30ac90f9a315");
        assertThat(preBidResponse.bids).extracting(b -> b.cacheUrl).containsOnly("cached_asset_url");
    }

    @Test
    public void shouldNotInteractWithCacheServiceIfRequestHasBidsAndNoCacheMarkupFlag() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1BidCustomizable(identity());

        givenAdapterRespondingWithBids(rubiconAdapter, RUBICON, "bidId1");

        // when
        auctionHandler.auction(routingContext);

        // then
        verifyZeroInteractions(cacheService);

        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.bids).extracting(b -> b.cacheId).containsNull();
        assertThat(preBidResponse.bids).extracting(b -> b.cacheUrl).containsNull();
    }

    @Test
    public void shouldNotInteractWithCacheServiceIfRequestHasNoBidsButCacheMarkupFlag() throws IOException {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1BidCustomizable(builder -> builder.cacheMarkup(1));

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
        givenPreBidRequestContextWith2AdUnitsAnd2BidsEach();

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

    @Test
    public void shouldTolerateUnsupportedBidderInPreBidRequest() throws IOException {
        // given
        final List<Bidder> bidders = asList(
                Bidder.from("unsupported", singletonList(null)),
                Bidder.from(RUBICON, singletonList(null)));
        givenPreBidRequestContextCustomizable(identity(), builder -> builder.bidders(bidders));

        givenAdapterRespondingWithBids(rubiconAdapter, RUBICON, "bidId1");

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.bidderStatus).extracting(b -> b.bidder, b -> b.error).containsOnly(
                tuple("unsupported", "Unsupported bidder"),
                tuple(RUBICON, null));
        assertThat(preBidResponse.bids).hasSize(1);
    }

    @Test
    public void shouldTolerateErrorResultFromAdapter() throws IOException {
        // given
        givenPreBidRequestContextWith2AdUnitsAnd2BidsEach();

        givenAdapterRespondingWithError(rubiconAdapter, RUBICON, "rubicon error", false);
        givenAdapterRespondingWithBids(appnexusAdapter, APPNEXUS, "bidId1");

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.bidderStatus).extracting(b -> b.bidder, b -> b.error).containsOnly(
                tuple(RUBICON, "rubicon error"),
                tuple(APPNEXUS, null));
        assertThat(preBidResponse.bids).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncrementCommonMetrics() {
        // given
        givenPreBidRequestContextCustomizable(builder -> builder.app(App.builder().build()));

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
        verify(metrics).forAccount(eq("accountId"));
        verify(accountMetrics).incCounter(eq(MetricName.requests));
        verify(metrics).updateTimer(eq(MetricName.request_time), anyLong());
        verify(metrics, never()).incCounter(eq(MetricName.safari_requests));
        verify(metrics, never()).incCounter(eq(MetricName.no_cookie_requests));
        verify(metrics, never()).incCounter(eq(MetricName.safari_no_cookie_requests));
        verify(metrics, never()).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementSafariAndNoCookieMetrics() {
        // given
        givenPreBidRequestContext();

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
        givenPreBidRequestContextWith1AdUnitAnd1BidCustomizable(identity());

        given(applicationSettings.getAccountById(any())).willReturn(Optional.empty());

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfRequestIsNotValid() {
        // given
        given(preBidRequestContextFactory.fromRequest(any())).willThrow(new PreBidRequestException("Could not create"));

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfAdapterReturnsError() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1BidCustomizable(identity());

        givenAdapterRespondingWithError(rubiconAdapter, RUBICON, "rubicon error", false);

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(metrics).forAdapter(eq(Adapter.Type.rubicon));
        // first invocation happens early during request validation phase
        verify(metrics, times(2)).forAccount(eq("accountId"));
        verify(accountMetrics).forAdapter(eq(Adapter.Type.rubicon));
        verify(adapterMetrics).incCounter(eq(MetricName.error_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.error_requests));
    }

    @Test
    public void shouldIncrementErrorMetricIfAdapterReturnsTimeoutError() {
        // given
        givenPreBidRequestContextWith1AdUnitAnd1BidCustomizable(identity());

        givenAdapterRespondingWithError(rubiconAdapter, RUBICON, "time out", true);

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(metrics).forAdapter(eq(Adapter.Type.rubicon));
        // first invocation happens early during request validation phase
        verify(metrics, times(2)).forAccount(eq("accountId"));
        verify(accountMetrics).forAdapter(eq(Adapter.Type.rubicon));
        verify(adapterMetrics).incCounter(eq(MetricName.timeout_requests));
        verify(accountAdapterMetrics).incCounter(eq(MetricName.timeout_requests));
    }

    private void givenPreBidRequestContextWith1AdUnitAnd1BidCustomizable(
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder>
                    preBidRequestBuilderCustomizer) {

        final List<Bidder> bidders = singletonList(Bidder.from(RUBICON, singletonList(null)));
        givenPreBidRequestContextCustomizable(preBidRequestBuilderCustomizer, builder -> builder.bidders(bidders));
    }

    private void givenPreBidRequestContextWith2AdUnitsAnd2BidsEach() {
        final List<AdUnitBid> adUnitBids = asList(null, null);
        final List<Bidder> bidders = asList(
                Bidder.from(RUBICON, adUnitBids),
                Bidder.from(APPNEXUS, adUnitBids));
        givenPreBidRequestContextCustomizable(identity(), builder -> builder.bidders(bidders));
    }

    private void givenPreBidRequestContextCustomizable(
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder>
                    preBidRequestBuilderCustomizer,
            Function<PreBidRequestContext.PreBidRequestContextBuilder, PreBidRequestContext.PreBidRequestContextBuilder>
                    preBidRequestContextBuilderCustomizer) {

        final PreBidRequest preBidRequest = preBidRequestBuilderCustomizer.apply(
                PreBidRequest.builder()
                        .tid("tid")
                        .accountId("accountId")
                        .adUnits(emptyList()))
                .build();
        final PreBidRequestContext preBidRequestContext = preBidRequestContextBuilderCustomizer.apply(
                PreBidRequestContext.builder()
                        .bidders(emptyList())
                        .preBidRequest(preBidRequest)
                        .uidsCookie(mock(UidsCookie.class)))
                .build();
        given(preBidRequestContextFactory.fromRequest(any())).willReturn(preBidRequestContext);
    }

    private void givenPreBidRequestContextCustomizable(
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder>
                    preBidRequestBuilderCustomizer) {

        givenPreBidRequestContextCustomizable(preBidRequestBuilderCustomizer, identity());
    }

    private void givenPreBidRequestContext() {
        givenPreBidRequestContextCustomizable(identity());
    }

    private void givenAdapterRespondingWithBids(Adapter adapter, String bidder, String... bidIds) {
        given(adapter.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.builder()
                        .bidderStatus(BidderStatus.builder().bidder(bidder).build())
                        .bids(Arrays.stream(bidIds)
                                .map(id -> org.rtb.vexing.model.response.Bid.builder().bidId(id).build())
                                .collect(Collectors.toList()))
                        .build()));
    }

    private void givenAdapterRespondingWithError(Adapter adapter, String bidder, String error, boolean timedOut) {
        given(adapter.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.builder()
                        .timedOut(timedOut)
                        .bidderStatus(BidderStatus.builder().bidder(bidder).error(error).build())
                        .build()));
    }

    private PreBidResponse capturePreBidResponse() throws IOException {
        final ArgumentCaptor<String> preBidResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse, times(1)).end(preBidResponseCaptor.capture());
        return mapper.readValue(preBidResponseCaptor.getValue(), PreBidResponse.class);
    }
}
