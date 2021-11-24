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
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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
    private HttpClient httpClient;
    @Mock
    private BidderErrorNotifier bidderErrorNotifier;
    @Mock
    private HttpBidderRequestEnricher requestEnricher;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;

    private HttpBidderRequester httpBidderRequester;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
        given(bidderErrorNotifier.processTimeout(any(), any())).will(invocation -> invocation.getArgument(0));
        given(routingContext.request()).willReturn(httpServerRequest);
        given(httpServerRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(requestEnricher.enrichHeaders(any(), any(), any())).willReturn(new CaseInsensitiveHeaders());

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        httpBidderRequester = new HttpBidderRequester(
                httpClient, null, bidderErrorNotifier, requestEnricher, jacksonMapper);
    }

    @Test
    public void shouldReturnFailedToRequestBidsErrorWhenBidderReturnsEmptyHttpRequestAndErrorLists() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(emptyList(), emptyList()));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false)
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

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false)
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
        final MultiMap headers = new CaseInsensitiveHeaders();
        headers.add("header1", "value1");
        headers.add("header2", "value2");
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri")
                                .body(EMPTY_BYTE_BODY)
                                .headers(headers)
                                .build()),
                emptyList()));

        final List<BidderBid> bids = asList(BidderBid.of(null, null, null), BidderBid.of(null, null, null));
        given(bidder.makeBids(any(), any())).willReturn(Result.of(bids, emptyList()));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", "storedResponse", BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid = httpBidderRequester
                .requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false)
                .result();

        // then
        verifyNoInteractions(httpClient);
        final ArgumentCaptor<HttpCall<BidRequest>> httpCallArgumentCaptor = ArgumentCaptor.forClass(HttpCall.class);
        verify(bidder).makeBids(httpCallArgumentCaptor.capture(), any());
        assertThat(httpCallArgumentCaptor.getValue().getResponse())
                .extracting(HttpResponse::getBody)
                .isEqualTo("storedResponse");
        assertThat(bidderSeatBid.getBids()).containsOnlyElementsOf(bids);
    }

    @Test
    public void shouldMakeRequestToBidderWhenStoredResponseDefinedButBidderCreatesMoreThanOneRequest() {
        // given
        givenHttpClientResponse(200, null);
        final MultiMap headers = new CaseInsensitiveHeaders();
        headers.add("header1", "value1");
        headers.add("header2", "value2");
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri")
                                .body(EMPTY_BYTE_BODY)
                                .headers(headers)
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri")
                                .body(EMPTY_BYTE_BODY)
                                .headers(headers)
                                .build()),
                emptyList()));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", "storedResponse", BidRequest.builder().build());

        // when
        httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false);

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

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false);

        // then
        verify(httpClient).request(any(), anyString(), any(), (byte[]) isNull(), anyLong());
    }

    @Test
    public void shouldSendMultipleRequests() throws JsonProcessingException {
        // given
        givenHttpClientResponse(200, null);
        final BidRequest bidRequest = givenBidRequest(identity());

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(mapper.writeValueAsBytes(bidRequest))
                                .headers(new CaseInsensitiveHeaders())
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(mapper.writeValueAsBytes(bidRequest))
                                .headers(new CaseInsensitiveHeaders())
                                .build()),
                emptyList()));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false);

        // then
        verify(httpClient, times(2)).request(any(), anyString(), any(), any(byte[].class), anyLong());
    }

    @Test
    public void shouldReturnBidsCreatedByBidder() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build()),
                emptyList()));

        givenHttpClientResponse(200, "responseBody");

        final List<BidderBid> bids = asList(BidderBid.of(null, null, null), BidderBid.of(null, null, null));
        given(bidder.makeBids(any(), any())).willReturn(Result.of(bids, emptyList()));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false)
                        .result();

        // then
        assertThat(bidderSeatBid.getBids()).containsOnlyElementsOf(bids);
    }

    @Test
    public void shouldCompressRequestBodyIfContentEncodingHeaderIsGzip() {
        // given
        final MultiMap headers = new CaseInsensitiveHeaders()
                .add(HttpUtil.CONTENT_ENCODING_HEADER, HttpHeaderValues.GZIP);
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build()),
                emptyList()));

        given(requestEnricher.enrichHeaders(any(), any(), any())).willReturn(headers);
        givenHttpClientResponse(200, "responseBody");
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false)
                .result();

        // then
        final ArgumentCaptor<byte[]> actualRequestBody = ArgumentCaptor.forClass(byte[].class);
        verify(httpClient).request(any(), anyString(), any(), actualRequestBody.capture(), anyLong());
        assertThat(actualRequestBody.getValue()).isNotSameAs(EMPTY_BYTE_BODY);
    }

    @Test
    public void shouldNotWaitForResponsesWhenAllDealsIsGathered() throws JsonProcessingException {
        // given
        httpBidderRequester = new HttpBidderRequester(httpClient, new DealsBidderRequestCompletionTrackerFactory(),
                bidderErrorNotifier, requestEnricher, jacksonMapper);

        final BidRequest bidRequest = bidRequestWithDeals("deal1", "deal2");
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, bidRequest);
        final BidRequest firstRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("r1"));
        final byte[] firstRequestBody = mapper.writeValueAsBytes(firstRequest);
        final BidRequest secondRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("r2"));
        final byte[] secondRequestBody = mapper.writeValueAsBytes(secondRequest);
        final BidRequest thirdRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("r3"));
        final byte[] thirdRequestBody = mapper.writeValueAsBytes(thirdRequest);
        final BidRequest forthRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("r4"));
        final byte[] forthRequestBody = mapper.writeValueAsBytes(forthRequest);

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(Arrays.asList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(firstRequestBody)
                                .headers(new CaseInsensitiveHeaders())
                                .payload(bidRequestWithDeals("deal1"))
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(secondRequestBody)
                                .headers(new CaseInsensitiveHeaders())
                                .payload(bidRequestWithDeals("deal1"))
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(thirdRequestBody)
                                .headers(new CaseInsensitiveHeaders())
                                .payload(bidRequestWithDeals("deal2"))
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(forthRequestBody)
                                .headers(new CaseInsensitiveHeaders())
                                .payload(bidRequestWithDeals("deal1"))
                                .build()),
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
        given(bidder.makeBids(any(), any())).willReturn(
                Result.of(singletonList(bidderBidDeal1), emptyList()),
                Result.of(singletonList(bidderBidDeal2), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(
                                bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false)
                        .result();

        // then
        verify(bidder).makeHttpRequests(any());
        verify(httpClient, times(4)).request(any(), any(), any(), any(byte[].class), anyLong());
        verify(bidder, times(2)).makeBids(any(), any());

        assertThat(bidderSeatBid.getBids()).containsOnly(bidderBidDeal1, bidderBidDeal2);
    }

    @Test
    public void shouldFinishWhenAllDealRequestsAreFinishedAndNoDealsProvided() {
        // given
        final BidRequest bidRequest = bidRequestWithDeals("deal1", "deal2", "deal2");
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, bidRequest);

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(Arrays.asList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .payload(bidRequestWithDeals("deal1"))
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .payload(bidRequestWithDeals("deal2"))
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .payload(bidRequestWithDeals("deal2"))
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .payload(bidRequestWithDeals("deal2"))
                                .build()),
                emptyList()));

        givenHttpClientResponse(200, "responseBody");

        final BidderBid bidderBid = BidderBid.of(Bid.builder().dealid("deal2").build(), null, null);
        given(bidder.makeBids(any(), any())).willReturn(Result.of(singletonList(bidderBid), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(
                                bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false)
                        .result();

        // then
        verify(bidder).makeHttpRequests(any());
        verify(httpClient, times(4)).request(any(), any(), any(), any(byte[].class), anyLong());
        verify(bidder, times(4)).makeBids(any(), any());

        assertThat(bidderSeatBid.getBids()).contains(bidderBid, bidderBid, bidderBid, bidderBid);
    }

    @Test
    public void shouldReturnFullDebugInfoIfDebugEnabled() throws JsonProcessingException {
        // given
        final MultiMap headers = new CaseInsensitiveHeaders().add("headerKey", "headerValue");
        final BidRequest firstBidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("firstId"));
        final BidRequest secondBidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.id("secondId"));
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri1")
                                .body(mapper.writeValueAsBytes(firstBidRequest))
                                .payload(firstBidRequest)
                                .headers(headers)
                                .build(),
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri2")
                                .body(mapper.writeValueAsBytes(secondBidRequest))
                                .payload(secondBidRequest)
                                .headers(headers)
                                .build()),
                emptyList()));

        given(requestEnricher.enrichHeaders(any(), any(), any())).willReturn(headers);

        givenHttpClientReturnsResponses(
                HttpClientResponse.of(200, null, "responseBody1"),
                HttpClientResponse.of(200, null, "responseBody2"));

        given(bidder.makeBids(any(), any())).willReturn(Result.of(emptyList(), emptyList()));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), true)
                        .result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).hasSize(2).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody(mapper.writeValueAsString(firstBidRequest))
                        .responsebody("responseBody1")
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .status(200).build(),
                ExtHttpCall.builder().uri("uri2").requestbody(mapper.writeValueAsString(secondBidRequest))
                        .responsebody("responseBody2")
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .status(200).build());
    }

    @Test
    public void shouldNotReturnSensitiveHeadersInFullDebugInfo() {
        // given
        final CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
        headers.add("headerKey", "headerValue");
        headers.add("Authorization", "authorizationValue");
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri1")
                                .body(EMPTY_BYTE_BODY)
                                .headers(headers)
                                .build()),
                emptyList()));
        given(requestEnricher.enrichHeaders(any(), any(), any())).willReturn(headers);

        givenHttpClientReturnsResponses(
                HttpClientResponse.of(200, null, "responseBody1"));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester
                        .requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), true).result();

        // then
        assertThat(bidderSeatBid.getHttpCalls())
                .extracting(ExtHttpCall::getRequestheaders)
                .flatExtracting(Map::keySet)
                .containsExactly("headerKey");
    }

    @Test
    public void shouldReturnPartialDebugInfoIfDebugEnabledAndGlobalTimeoutAlreadyExpired()
            throws JsonProcessingException {
        // given
        final MultiMap headers = new CaseInsensitiveHeaders().add("headerKey", "headerValue");
        final BidRequest givenBidRequest = givenBidRequest(identity());
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri1")
                                .headers(headers)
                                .payload(givenBidRequest)
                                .body(mapper.writeValueAsBytes(givenBidRequest))
                                .build()),
                emptyList()));

        given(requestEnricher.enrichHeaders(any(), any(), any())).willReturn(headers);

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(bidder, bidderRequest, expiredTimeout, CaseInsensitiveMultiMap.empty(),
                        true).result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).hasSize(1).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody(mapper.writeValueAsString(givenBidRequest))
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .build());
    }

    @Test
    public void shouldReturnPartialDebugInfoIfDebugEnabledAndHttpErrorOccurs() throws JsonProcessingException {
        // given
        final MultiMap headers = new CaseInsensitiveHeaders().add("headerKey", "headerValue");
        final BidRequest givenBidRequest = givenBidRequest(identity());
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri1")
                                .body(mapper.writeValueAsBytes(givenBidRequest))
                                .payload(givenBidRequest)
                                .headers(headers)
                                .build()),
                emptyList()));

        given(requestEnricher.enrichHeaders(any(), any(), any())).willReturn(headers);

        givenHttpClientProducesException(new RuntimeException("Request exception"));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), true)
                        .result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).hasSize(1).containsOnly(
                ExtHttpCall.builder().uri("uri1")
                        .requestbody(mapper.writeValueAsString(givenBidRequest))
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .build());
    }

    @Test
    public void shouldReturnFullDebugInfoIfDebugEnabledAndErrorStatus() throws JsonProcessingException {
        // given
        final MultiMap headers = new CaseInsensitiveHeaders().add("headerKey", "headerValue");
        final BidRequest givenBidRequest = givenBidRequest(identity());
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri("uri1")
                                .body(mapper.writeValueAsBytes(givenBidRequest))
                                .payload(givenBidRequest)
                                .headers(headers)
                                .build()),
                emptyList()));

        given(requestEnricher.enrichHeaders(any(), any(), any())).willReturn(headers);

        givenHttpClientReturnsResponses(HttpClientResponse.of(500, null, "responseBody1"));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), true)
                        .result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).hasSize(1).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody(mapper.writeValueAsString(givenBidRequest))
                        .responsebody("responseBody1")
                        .requestheaders(singletonMap("headerKey", singletonList("headerValue")))
                        .status(500).build());
        assertThat(bidderSeatBid.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage).containsOnly(
                        "Unexpected status code: 500. Run with request.test = 1 for more info");
    }

    @Test
    public void shouldTolerateAlreadyExpiredGlobalTimeout() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build()),
                emptyList()));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid =
                httpBidderRequester.requestBids(bidder, bidderRequest, expiredTimeout, CaseInsensitiveMultiMap.empty(),
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
        final HttpRequest<BidRequest> httpRequest = HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(EMPTY)
                .body(EMPTY_BYTE_BODY)
                .build();

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(httpRequest), null));

        given(httpClient.request(any(), anyString(), any(), any(byte[].class), anyLong()))
                // bidder request
                .willReturn(Future.failedFuture(new TimeoutException("Timeout exception")));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false);

        // then
        verify(bidderErrorNotifier).processTimeout(any(), same(bidder));
    }

    @Test
    public void shouldTolerateMultipleErrors() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                        // this request will fail with response exception
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build(),
                        // this request will fail with timeout
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build(),
                        // this request will fail with 500 status
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build(),
                        // this request will fail with 400 status
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build(),
                        // this request will get 204 status
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build(),
                        // finally this request will succeed
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build()),
                singletonList(BidderError.badInput("makeHttpRequestsError"))));
        when(requestEnricher.enrichHeaders(any(), any(), any())).thenAnswer(invocation -> new CaseInsensitiveHeaders());
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

        given(bidder.makeBids(any(), any())).willReturn(
                Result.of(singletonList(BidderBid.of(Bid.builder().impid("123").build(), null, null)),
                        singletonList(BidderError.badServerResponse("makeBidsError"))));

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().build());

        // when
        final BidderSeatBid bidderSeatBid = httpBidderRequester
                .requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false)
                .result();

        // then
        // only one calls is expected (200) since other requests have failed with errors.
        verify(bidder).makeBids(any(), any());
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
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(EMPTY)
                                .body(EMPTY_BYTE_BODY)
                                .headers(new CaseInsensitiveHeaders())
                                .build()),
                emptyList()));

        givenHttpClientResponse(204, EMPTY);

        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, BidRequest.builder().test(1).build());

        // when
        httpBidderRequester.requestBids(bidder, bidderRequest, timeout, CaseInsensitiveMultiMap.empty(), false);

        // then
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
                .collect(Collectors.toList());
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

    @AllArgsConstructor
    public static class MultiMapMatcher implements ArgumentMatcher<MultiMap> {

        private final MultiMap left;

        @Override
        public boolean matches(MultiMap right) {
            return left.size() == right.size() && left.entries().stream()
                    .allMatch(entry -> right.contains(entry.getKey(), entry.getValue(), true));
        }
    }
}
