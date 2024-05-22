package org.prebid.server.bidder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HttpBidderRequesterTest extends VertxTest {

    private static final byte[] EMPTY_BYTE_BODY = "{}".getBytes();
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Bidder<BidRequest> bidder;
    @Mock
    private BidRejectionTracker bidRejectionTracker;
    @Mock
    private BidderAliases bidderAliases;
    @Mock
    private HttpClient httpClient;
    @Mock
    private BidderErrorNotifier bidderErrorNotifier;
    @Mock
    private HttpBidderRequestEnricher requestEnricher;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;

    private HttpBidderRequester target;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
        given(bidderErrorNotifier.processTimeout(any(), any())).will(invocation -> invocation.getArgument(0));
        given(routingContext.request()).willReturn(httpServerRequest);
        given(httpServerRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any()))
                .willReturn(MultiMap.caseInsensitiveMultiMap());

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        target = new HttpBidderRequester(
                httpClient, null, bidderErrorNotifier, requestEnricher, jacksonMapper);
        given(bidder.makeBidderResponse(any(BidderCall.class), any(BidRequest.class))).willCallRealMethod();
    }

    @Test
    public void shouldReturnFailedToRequestBidsErrorWhenBidderReturnsEmptyHttpRequestAndErrorLists() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(emptyList(), emptyList()));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target.requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                false)
                        .result();

        // then
        assertThat(bidderSeatBid.getBids()).isEmpty();
        assertThat(bidderSeatBid.getHttpCalls()).isEmpty();
        assertThat(bidderSeatBid.getErrors())
                .containsOnly(BidderError.failedToRequestBids(
                        "The bidder failed to generate any bid requests, but also failed to generate an error"));
    }

    @Test
    public void shouldTolerateBidderReturningErrorsAndNoHttpRequests() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(emptyList(),
                asList(BidderError.badInput("error1"), BidderError.badInput("error2"))));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target.requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                false)
                        .result();

        // then
        assertThat(bidderSeatBid.getBids()).isEmpty();
        assertThat(bidderSeatBid.getHttpCalls()).isEmpty();
        assertThat(bidderSeatBid.getErrors())
                .extracting(BidderError::getMessage).containsOnly("error1", "error2");
    }

    @Test
    public void shouldPassStoredResponseToBidderMakeBidsMethodAndReturnSeatBids() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("header1", "value1");
        headers.add("header2", "value2");
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder.uri("uri"))),
                emptyList()));

        final List<BidderBid> bids = asList(BidderBid.of(null, null, null), BidderBid.of(null, null, null));
        given(bidder.makeBidderResponse(any(), any())).willReturn(CompositeBidderResponse.withBids(bids, emptyList()));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .storedResponse("storedResponse")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid = target
                .requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        timeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        false)
                .result();

        // then
        verifyNoInteractions(httpClient);
        final ArgumentCaptor<BidderCall<BidRequest>> httpCallArgumentCaptor =
                ArgumentCaptor.forClass(BidderCall.class);
        verify(bidder).makeBidderResponse(httpCallArgumentCaptor.capture(), any());
        assertThat(httpCallArgumentCaptor.getValue().getResponse())
                .extracting(HttpResponse::getBody)
                .isEqualTo("storedResponse");
        assertThat(bidderSeatBid.getBids()).hasSameElementsAs(bids);
    }

    @Test
    public void shouldMakeRequestToBidderWhenStoredResponseDefinedButBidderCreatesMoreThanOneRequest() {
        // given
        givenHttpClientResponse(200, null);
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("header1", "value1");
        headers.add("header2", "value2");
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .uri("uri")
                                .headers(headers)),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .uri("uri")
                                .headers(headers))),
                emptyList()));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .storedResponse("storedResponse")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        target.requestBids(
                bidder,
                bidderRequest,
                bidRejectionTracker,
                timeout,
                CaseInsensitiveMultiMap.empty(),
                bidderAliases,
                false);

        // then
        verify(httpClient, times(2)).request(any(), anyString(), any(), any(byte[].class), anyLong());
    }

    @Test
    public void shouldSendPopulatedGetRequestWithoutBody() {
        // given
        givenHttpClientResponse(200, null);

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.GET)
                                .uri("uri")
                                .build()),
                emptyList()));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        target
                .requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        timeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        false);

        // then
        verify(httpClient).request(any(), anyString(), any(), (byte[]) isNull(), anyLong());
    }

    @Test
    public void shouldSendMultipleRequests() throws JsonProcessingException {
        // given
        givenHttpClientResponse(200, null);
        final BidRequest bidRequest = givenBidRequest(identity());
        final byte[] body = mapper.writeValueAsBytes(bidRequest);

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .body(body)),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .body(body))),
                emptyList()));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        target.requestBids(
                bidder,
                bidderRequest,
                bidRejectionTracker,
                timeout,
                CaseInsensitiveMultiMap.empty(),
                bidderAliases,
                false);

        // then
        verify(httpClient, times(2)).request(any(), anyString(), any(), any(byte[].class), anyLong());
    }

    @Test
    public void shouldReturnBidsCreatedByBidder() {
        // given
        givenSuccessfulBidderMakeHttpRequests();

        final List<BidderBid> bids = asList(BidderBid.of(null, null, null), BidderBid.of(null, null, null));
        given(bidder.makeBidderResponse(any(), any())).willReturn(CompositeBidderResponse.withBids(bids, emptyList()));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target
                        .requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                false)
                        .result();

        // then
        assertThat(bidderSeatBid.getBids()).hasSameElementsAs(bids);
    }

    @Test
    public void shouldReturnBidsCreatedByMakeBids() {
        // given
        givenSuccessfulBidderMakeHttpRequests();

        final List<BidderBid> bids = emptyList();
        given(bidder.makeBidderResponse(any(), any()))
                .willReturn(CompositeBidderResponse.withBids(bids, null));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target
                        .requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                false)
                        .result();

        // then
        assertThat(bidderSeatBid.getBids()).hasSameElementsAs(bids);
    }

    @Test
    public void shouldReturnFledgeCreatedByBidder() {
        // given
        givenSuccessfulBidderMakeHttpRequests();

        final List<FledgeAuctionConfig> fledgeAuctionConfigs = List.of(
                givenFledgeAuctionConfig("imp-1"),
                givenFledgeAuctionConfig("imp-2"));
        final List<BidderBid> bids = emptyList();

        given(bidder.makeBidderResponse(any(), any()))
                .willReturn(CompositeBidderResponse.withBids(bids, fledgeAuctionConfigs));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target
                        .requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                false)
                        .result();

        // then
        assertThat(bidderSeatBid.getBids()).hasSameElementsAs(bids);
        assertThat(bidderSeatBid.getFledgeAuctionConfigs()).hasSameElementsAs(fledgeAuctionConfigs);
    }

    @Test
    public void shouldCompressRequestBodyIfContentEncodingHeaderIsGzip() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.CONTENT_ENCODING_HEADER, HttpHeaderValues.GZIP);
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(
                singletonList(givenSimpleHttpRequest(identity())),
                emptyList()));

        given(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any())).willReturn(headers);
        givenHttpClientResponse(200, "responseBody");
        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        target.requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        timeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        false)
                .result();

        // then
        final ArgumentCaptor<byte[]> actualRequestBody = ArgumentCaptor.forClass(byte[].class);
        verify(httpClient).request(any(), anyString(), any(), actualRequestBody.capture(), anyLong());
        assertThat(actualRequestBody.getValue()).isNotSameAs(EMPTY_BYTE_BODY);
    }

    @Test
    public void shouldNotWaitForResponsesWhenAllDealsIsGathered() throws JsonProcessingException {
        // given
        target = new HttpBidderRequester(
                httpClient,
                bidRequest -> new BidderRequestCompletionTracker() {

                    private final AtomicInteger waitAllDeals = new AtomicInteger(2);
                    private final Promise<Void> promise = Promise.promise();

                    @Override
                    public Future<Void> future() {
                        return promise.future();
                    }

                    @Override
                    public void processBids(List<BidderBid> bids) {
                        if (waitAllDeals.decrementAndGet() <= 0) {
                            promise.complete();
                        }
                    }
                },
                bidderErrorNotifier,
                requestEnricher,
                jacksonMapper);

        final BidRequest bidRequest = bidRequestWithDeals("deal1", "deal2");
        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(bidRequest)
                .build();
        final BidRequest firstRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("r1"));
        final byte[] firstRequestBody = mapper.writeValueAsBytes(firstRequest);
        final BidRequest secondRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("r2"));
        final byte[] secondRequestBody = mapper.writeValueAsBytes(secondRequest);
        final BidRequest thirdRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("r3"));
        final byte[] thirdRequestBody = mapper.writeValueAsBytes(thirdRequest);
        final BidRequest forthRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("r4"));
        final byte[] forthRequestBody = mapper.writeValueAsBytes(forthRequest);

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(Arrays.asList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .body(firstRequestBody)
                                .payload(bidRequestWithDeals("deal1"))),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .body(secondRequestBody)
                                .payload(bidRequestWithDeals("deal1"))),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .body(thirdRequestBody)
                                .payload(bidRequestWithDeals("deal2"))),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .body(forthRequestBody)
                                .payload(bidRequestWithDeals("deal1")))),
                emptyList()));

        final HttpClientResponse respWithDeal1 = HttpClientResponse.of(200, null,
                "{\"seatbid\":[{\"bid\":[{\"dealid\":\"deal1\"}]}]}");
        final HttpClientResponse respWithDeal2 = HttpClientResponse.of(200, null,
                "{\"seatbid\":[{\"bid\":[{\"dealid\":\"deal2\"}]}]}");

        given(httpClient.request(any(), anyString(), any(), eq(firstRequestBody), anyLong()))
                .willReturn(Future.succeededFuture(respWithDeal1));
        given(httpClient.request(any(), anyString(), any(), eq(secondRequestBody), anyLong()))
                .willReturn(Promise.<HttpClientResponse>promise().future());
        given(httpClient.request(any(), anyString(), any(), eq(thirdRequestBody), anyLong()))
                .willReturn(Future.succeededFuture(respWithDeal2));
        given(httpClient.request(any(), anyString(), any(), eq(forthRequestBody), anyLong()))
                .willReturn(Promise.<HttpClientResponse>promise().future());

        final BidderBid bidderBidDeal1 = BidderBid.of(Bid.builder().impid("deal1").dealid("deal1").build(), null, null);
        final BidderBid bidderBidDeal2 = BidderBid.of(Bid.builder().impid("deal2").dealid("deal2").build(), null, null);
        given(bidder.makeBidderResponse(any(), any())).willReturn(
                CompositeBidderResponse.withBids(singletonList(bidderBidDeal1), emptyList()),
                CompositeBidderResponse.withBids(singletonList(bidderBidDeal2), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid = target.requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        timeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        false)
                .result();

        // then
        verify(bidder).makeHttpRequests(any());
        verify(httpClient, times(4)).request(any(), any(), any(), any(byte[].class), anyLong());
        verify(bidder, times(2)).makeBidderResponse(any(), any());

        assertThat(bidderSeatBid.getBids()).containsOnly(bidderBidDeal1, bidderBidDeal2);
    }

    @Test
    public void shouldFinishWhenAllDealRequestsAreFinishedAndNoDealsProvided() {
        // given
        final BidRequest bidRequest = bidRequestWithDeals("deal1", "deal2", "deal2");
        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(bidRequest)
                .build();

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(Arrays.asList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .payload(bidRequestWithDeals("deal1"))),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .payload(bidRequestWithDeals("deal2"))),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .payload(bidRequestWithDeals("deal2"))),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .payload(bidRequestWithDeals("deal2")))),
                emptyList()));

        givenHttpClientResponse(200, "responseBody");

        final BidderBid bidderBid = BidderBid.of(Bid.builder().dealid("deal2").build(), null, null);
        given(bidder.makeBidderResponse(any(), any())).willReturn(
                CompositeBidderResponse.withBids(singletonList(bidderBid), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid =
                target.requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                false)
                        .result();

        // then
        verify(bidder).makeHttpRequests(any());
        verify(httpClient, times(4)).request(any(), any(), any(), any(byte[].class), anyLong());
        verify(bidder, times(4)).makeBidderResponse(any(), any());

        assertThat(bidderSeatBid.getBids()).contains(bidderBid, bidderBid, bidderBid, bidderBid);
    }

    @Test
    public void shouldReturnFullDebugInfoIfDebugEnabled() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("headerKey", "headerValue");
        final BidRequest firstBidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("firstId"));
        final BidRequest secondBidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("secondId"));
        final byte[] firstRequestBody = mapper.writeValueAsBytes(firstBidRequest);
        final byte[] secondRequestBody = mapper.writeValueAsBytes(secondBidRequest);
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .uri("uri1")
                                .body(firstRequestBody)
                                .payload(firstBidRequest)
                                .headers(headers)),
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .uri("uri2")
                                .body(secondRequestBody)
                                .payload(secondBidRequest)
                                .headers(headers))),
                emptyList()));

        given(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any())).willReturn(headers);

        givenHttpClientReturnsResponses(
                HttpClientResponse.of(200, null, "responseBody1"),
                HttpClientResponse.of(200, null, "responseBody2"));

        given(bidder.makeBidderResponse(any(), any())).willReturn(CompositeBidderResponse.empty());

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target.requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                true)
                        .result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).containsExactlyInAnyOrder(
                ExtHttpCall.builder()
                        .uri("uri1")
                        .requestbody(mapper.writeValueAsString(firstBidRequest))
                        .responsebody("responseBody1")
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .status(200)
                        .build(),
                ExtHttpCall.builder()
                        .uri("uri2")
                        .requestbody(mapper.writeValueAsString(secondBidRequest))
                        .responsebody("responseBody2")
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .status(200)
                        .build());
    }

    @Test
    public void shouldReturnRecordBidRejections() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("headerKey", "headerValue");

        final Imp imp2 = Imp.builder().id("2").build();
        final Imp imp3 = Imp.builder().id("3").build();

        final BidRequest firstBidRequest = givenBidRequest(builder -> builder.id("firstId").imp(singletonList(imp2)));
        final BidRequest secondBidRequest = givenBidRequest(builder -> builder.id("secondId").imp(singletonList(imp3)));
        final byte[] firstRequestBody = mapper.writeValueAsBytes(firstBidRequest);
        final byte[] secondRequestBody = mapper.writeValueAsBytes(secondBidRequest);
        final HttpRequest<BidRequest> firstRequest = givenSimpleHttpRequest(
                httpRequestBuilder -> httpRequestBuilder
                        .uri("uri1")
                        .body(firstRequestBody)
                        .payload(firstBidRequest)
                        .impIds(singleton("2"))
                        .headers(headers));
        final HttpRequest<BidRequest> secondRequest = givenSimpleHttpRequest(
                httpRequestBuilder -> httpRequestBuilder
                        .uri("uri2")
                        .body(secondRequestBody)
                        .payload(secondBidRequest)
                        .impIds(singleton("3"))
                        .headers(headers));
        given(bidder.makeHttpRequests(any())).willReturn(
                Result.of(
                        List.of(firstRequest, secondRequest),
                        singletonList(BidderError.rejectedIpf("error", "1"))));

        given(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any())).willReturn(headers);

        givenHttpClientReturnsResponses(
                HttpClientResponse.of(200, null, "responseBody1"),
                HttpClientResponse.of(400, null, null));

        final List<BidderBid> secondRequestBids = singletonList(BidderBid.builder()
                .bid(Bid.builder().impid("2").build())
                .build());
        given(bidder.makeBidderResponse(any(), any()))
                .willReturn(CompositeBidderResponse.withBids(secondRequestBids, null));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        target.requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        timeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        true)
                .result();

        // then
        verify(bidRejectionTracker, atLeast(1)).succeed(secondRequestBids);
        verify(bidRejectionTracker).reject(singleton("1"), BidRejectionReason.REJECTED_DUE_TO_PRICE_FLOOR);
        verify(bidRejectionTracker).reject(singleton("3"), BidRejectionReason.OTHER_ERROR);
    }

    @Test
    public void shouldNotReturnSensitiveHeadersInFullDebugInfo()
            throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("headerKey", "headerValue");
        headers.add("Authorization", "authorizationValue");
        final BidRequest givenBidRequest = givenBidRequest(identity());
        final byte[] requestBody = mapper.writeValueAsBytes(givenBidRequest);
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .uri("uri1")
                                .headers(headers)
                                .payload(givenBidRequest)
                                .body(requestBody))),
                emptyList()));

        given(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any())).willReturn(headers);

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target.requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                expiredTimeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                true)
                        .result();

        // then
        assertThat(bidderSeatBid.getHttpCalls())
                .extracting(ExtHttpCall::getRequestheaders)
                .containsExactly(singletonMap("headerKey", singletonList("headerValue")));
    }

    @Test
    public void shouldReturnPartialDebugInfoIfDebugEnabledAndGlobalTimeoutAlreadyExpired()
            throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("headerKey", "headerValue");
        final BidRequest givenBidRequest = givenBidRequest(identity());
        final byte[] requestBody = mapper.writeValueAsBytes(givenBidRequest);
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .uri("uri1")
                                .headers(headers)
                                .payload(givenBidRequest)
                                .body(requestBody))),
                emptyList()));

        given(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any())).willReturn(headers);

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target.requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                expiredTimeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                true)
                        .result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).containsExactly(
                ExtHttpCall.builder()
                        .uri("uri1")
                        .requestbody(mapper.writeValueAsString(givenBidRequest))
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .build());
    }

    @Test
    public void shouldReturnPartialDebugInfoIfDebugEnabledAndHttpErrorOccurs() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("headerKey", "headerValue");
        final BidRequest givenBidRequest = givenBidRequest(identity());
        final byte[] requestBody = mapper.writeValueAsBytes(givenBidRequest);
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .uri("uri1")
                                .headers(headers)
                                .payload(givenBidRequest)
                                .body(requestBody))),
                emptyList()));

        given(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any())).willReturn(headers);

        givenHttpClientProducesException(new RuntimeException("Request exception"));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target
                        .requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                true)
                        .result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).containsExactly(
                ExtHttpCall.builder()
                        .uri("uri1")
                        .requestbody(mapper.writeValueAsString(givenBidRequest))
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .build());
    }

    @Test
    public void shouldReturnFullDebugInfoIfDebugEnabledAndErrorStatus() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("headerKey", "headerValue");
        final BidRequest givenBidRequest = givenBidRequest(identity());
        final byte[] requestBody = mapper.writeValueAsBytes(givenBidRequest);
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        givenSimpleHttpRequest(httpRequestBuilder -> httpRequestBuilder
                                .uri("uri1")
                                .headers(headers)
                                .payload(givenBidRequest)
                                .body(requestBody))),
                emptyList()));

        given(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any())).willReturn(headers);

        givenHttpClientReturnsResponses(HttpClientResponse.of(500, null, "responseBody1"));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target
                        .requestBids(
                                bidder,
                                bidderRequest,
                                bidRejectionTracker,
                                timeout,
                                CaseInsensitiveMultiMap.empty(),
                                bidderAliases,
                                true)
                        .result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).containsExactly(
                ExtHttpCall.builder()
                        .uri("uri1")
                        .requestbody(mapper.writeValueAsString(givenBidRequest))
                        .responsebody("responseBody1")
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .status(500).build());

        assertThat(bidderSeatBid.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Unexpected status code: 500. Run with request.test = 1 for more info");
    }

    @Test
    public void shouldTolerateAlreadyExpiredGlobalTimeout() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(
                singletonList(givenSimpleHttpRequest(identity())),
                emptyList()));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid =
                target.requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        expiredTimeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        false).result();

        // then
        assertThat(bidderSeatBid.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsOnly("Timeout has been exceeded");
        verifyNoInteractions(httpClient);
    }

    @Test
    public void shouldNotifyBidderOfTimeout() {
        // given
        final HttpRequest<BidRequest> httpRequest = givenSimpleHttpRequest(builder -> builder.impIds(singleton("1")));

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(httpRequest), emptyList()));

        given(httpClient.request(any(), anyString(), any(), any(byte[].class), anyLong()))
                // bidder request
                .willReturn(Future.failedFuture(new TimeoutException("Timeout exception")));

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        target
                .requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        timeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        false);

        // then
        verify(bidderErrorNotifier).processTimeout(any(), same(bidder));
        verify(bidRejectionTracker).reject(singleton("1"), BidRejectionReason.TIMED_OUT);
    }

    @Test
    public void shouldTolerateMultipleErrors() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                        // this request will fail with response exception
                        givenSimpleHttpRequest(identity()),
                        // this request will fail with timeout
                        givenSimpleHttpRequest(identity()),
                        // this request will fail with 500 status
                        givenSimpleHttpRequest(identity()),
                        // this request will fail with 400 status
                        givenSimpleHttpRequest(identity()),
                        // this request will get 204 status
                        givenSimpleHttpRequest(identity()),
                        // finally this request will succeed
                        givenSimpleHttpRequest(identity())),
                singletonList(BidderError.badInput("makeHttpRequestsError"))));
        when(requestEnricher.enrichHeaders(anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> MultiMap.caseInsensitiveMultiMap());
        given(httpClient.request(any(), anyString(), any(), any(byte[].class), anyLong()))
                // simulate response error for the first request
                .willReturn(Future.failedFuture(new RuntimeException("Response exception")))
                // simulate timeout for the second request
                .willReturn(Future.failedFuture(new TimeoutException("Timeout exception")))
                // simulate 500 status
                .willReturn(Future.succeededFuture(HttpClientResponse.of(500, null, EMPTY)))
                // simulate 400 status
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, null, EMPTY)))
                // simulate 204 status
                .willReturn(Future.succeededFuture(HttpClientResponse.of(204, null, EMPTY)))
                // simulate 200 status
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, EMPTY)));

        given(bidder.makeBidderResponse(any(), any())).willReturn(
                CompositeBidderResponse.builder()
                        .bids(singletonList(BidderBid.of(Bid.builder().impid("123").build(), null, null)))
                        .errors(singletonList(BidderError.badServerResponse("makeBidsError")))
                        .build());

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().build())
                .build();

        // when
        final BidderSeatBid bidderSeatBid = target
                .requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        timeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        false)
                .result();

        // then
        // only one calls is expected (200) since other requests have failed with errors.
        verify(bidder).makeBidderResponse(any(), any());
        assertThat(bidderSeatBid.getBids()).hasSize(1);
        assertThat(bidderSeatBid.getErrors()).containsOnly(
                BidderError.badInput("makeHttpRequestsError"),
                BidderError.generic("Response exception"),
                BidderError.timeout("Timeout exception"),
                BidderError.badServerResponse("Unexpected status code: 500. Run with request.test = 1 for more info"),
                BidderError.badInput("Unexpected status code: 400. Run with request.test = 1 for more info"),
                BidderError.badServerResponse("makeBidsError"));
    }

    @Test
    public void shouldNotMakeBidsIfResponseStatusIs204() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(
                singletonList(givenSimpleHttpRequest(identity())),
                emptyList()));

        givenHttpClientResponse(204, EMPTY);

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(BidRequest.builder().test(1).build())
                .build();

        // when
        target
                .requestBids(
                        bidder,
                        bidderRequest,
                        bidRejectionTracker,
                        timeout,
                        CaseInsensitiveMultiMap.empty(),
                        bidderAliases,
                        false);

        // then
        verify(bidder, never()).makeBidderResponse(any(), any());
        verify(bidder, never()).makeBids(any(), any());
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .id("requestId")
                        .imp(singletonList(Imp.builder()
                                .id("impId")
                                .build())))
                .build();
    }

    private static BidRequest bidRequestWithDeals(String... ids) {
        final List<Imp> impsWithDeals = Arrays.stream(ids)
                .map(HttpBidderRequesterTest::impWithDeal)
                .toList();
        return BidRequest.builder().imp(impsWithDeals).build();
    }

    private static Imp impWithDeal(String dealId) {
        return Imp.builder()
                .id(dealId)
                .pmp(Pmp.builder()
                        .deals(singletonList(Deal.builder().id(dealId).build()))
                        .build())
                .build();
    }

    private static FledgeAuctionConfig givenFledgeAuctionConfig(String impId) {
        return FledgeAuctionConfig.builder()
                .impId(impId)
                .config(mapper.createObjectNode().put("references", impId))
                .build();
    }

    private static <T> HttpRequest<T> givenSimpleHttpRequest(
            UnaryOperator<HttpRequest.HttpRequestBuilder<T>> customizer) {
        return customizer.apply(HttpRequest.<T>builder()
                        .method(HttpMethod.POST)
                        .uri(EMPTY)
                        .body(EMPTY_BYTE_BODY)
                        .headers(MultiMap.caseInsensitiveMultiMap()))
                .build();
    }

    private void givenSuccessfulBidderMakeHttpRequests() {
        given(bidder.makeHttpRequests(any())).willReturn(
                Result.of(singletonList(givenSimpleHttpRequest(identity())), emptyList()));

        givenHttpClientResponse(200, "responseBody");
    }

    private void givenHttpClientResponse(int statusCode, String response) {
        given(httpClient.request(any(), anyString(), any(), (byte[]) any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(statusCode, null, response)));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        given(httpClient.request(any(), anyString(), any(), any(byte[].class), anyLong()))
                .willReturn(Future.failedFuture(throwable));
    }

    private void givenHttpClientReturnsResponses(HttpClientResponse... httpClientResponses) {
        BDDMockito.BDDMyOngoingStubbing<Future<HttpClientResponse>> stubbing =
                given(httpClient.request(any(), anyString(), any(), any(byte[].class), anyLong()));

        // setup multiple answers
        for (HttpClientResponse httpClientResponse : httpClientResponses) {
            stubbing = stubbing.willReturn(Future.succeededFuture(httpClientResponse));
        }
    }
}
