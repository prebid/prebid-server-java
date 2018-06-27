package org.prebid.server.bidder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.Bid.BidBuilder;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.BidResponse.BidResponseBuilder;
import com.iab.openrtb.response.SeatBid;
import com.iab.openrtb.response.SeatBid.SeatBidBuilder;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdUnitBid.AdUnitBidBuilder;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.AdapterResponse;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.auction.model.PreBidRequestContext.PreBidRequestContextBuilder;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.request.PreBidRequest.PreBidRequestBuilder;
import org.prebid.server.proto.response.BidderDebug;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.proto.response.UsersyncInfo;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;

public class HttpAdapterConnectorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Adapter<?, ?> adapter;
    @Mock
    private Usersyncer usersyncer;
    @Mock
    private HttpClient httpClient;
    private Clock clock;

    private HttpAdapterConnector httpAdapterConnector;

    private AdapterRequest adapterRequest;
    private PreBidRequestContext preBidRequestContext;
    @Mock
    private UidsCookie uidsCookie;
    @Mock
    private HttpClientRequest httpClientRequest;

    @Before
    public void setUp() {
        willReturn(singletonList(givenHttpRequest()))
                .given(adapter).makeHttpRequests(any(), any());
        willReturn(new TypeReference<BidResponse>() {
        }).given(adapter).responseTypeReference();

        given(httpClient.requestAbs(any(), anyString(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        adapterRequest = AdapterRequest.of(null, null);
        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        httpAdapterConnector = new HttpAdapterConnector(httpClient, clock);
    }

    @Test
    public void callShouldPerformHttpRequestsWithExpectedMethod() {
        // given
        willReturn(singletonList(givenHttpRequest(GET)))
                .given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verify(httpClient).requestAbs(eq(GET), anyString(), any());
    }

    @Test
    public void callShouldPerformHttpRequestsWithExpectedHeaders() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add("key1", "value1");
        willReturn(singletonList(AdapterHttpRequest.of(POST, "uri", null, headers)))
                .given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        assertThat(httpClientRequest.headers()).extracting(Map.Entry::getKey).containsOnly("key1");
        assertThat(httpClientRequest.headers()).extracting(Map.Entry::getValue).containsOnly("value1");
    }

    @Test
    public void callShouldPerformHttpRequestsWithoutAdditionalHeadersIfTheyAreNull() {
        // given
        willReturn(singletonList(givenHttpRequest(POST))).given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verify(httpClientRequest, never()).headers();
    }

    @Test
    public void callShouldPerformHttpRequestsWithExpectedTimeout() {
        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(httpClientRequest).setTimeout(timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue()).isEqualTo(500L);
    }

    @Test
    public void callShouldPerformHttpRequestsWithExpectedBody() throws IOException {
        // given
        willReturn(singletonList(AdapterHttpRequest.of(POST, "uri", givenBidRequest(b -> b.id("bidRequest1")), null)))
                .given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest).isNotNull();
        assertThat(bidRequest.getId()).isEqualTo("bidRequest1");
    }

    @Test
    public void callShouldPerformHttpRequestsWithoutBodyIfItIsNull() {
        // given
        willReturn(singletonList(givenHttpRequest(POST))).given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verify(httpClientRequest, never()).end(anyString());
        verify(httpClientRequest).end();
    }

    @Test
    public void callShouldNotPerformHttpRequestsIfAdapterReturnsEmptyHttpRequests() {
        // given
        given(adapter.makeHttpRequests(any(), any())).willReturn(emptyList());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfMakeHttpRequestsFails() {
        // given
        given(adapter.makeHttpRequests(any(), any())).willThrow(new PreBidException("Make http requests exception"));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.badInput("Make http requests exception"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Make http requests exception");
    }

    @Test
    public void callShouldSubmitTimeOutErrorToAdapterIfGlobalTimeoutAlreadyExpired() {
        // given
        preBidRequestContext = givenPreBidRequestContext(
                builder -> builder.timeout(expiredTimeout()),
                identity());

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.timeout("Timed out"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Timed out");
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void callShouldSubmitTimeOutErrorToAdapterIfConnectTimeoutOccurs() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new ConnectTimeoutException()));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.timeout("Timed out"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Timed out");
    }

    @Test
    public void callShouldSubmitTimeOutErrorToAdapterIfTimeoutOccurs() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new TimeoutException()));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.timeout("Timed out"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Timed out");
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.generic("Request exception"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Request exception");
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.generic("Response exception"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Response exception");
    }

    @Test
    public void callShouldNotSubmitErrorToAdapterIfHttpResponseStatusCodeIs204() {
        // given
        givenHttpClientReturnsResponses(204, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNull();
        assertThat(adapterResponse.getBidderStatus().getError()).isNull();
        assertThat(adapterResponse.getBids()).isEmpty();
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfHttpResponseStatusCodeIsNot200Or204() {
        // given
        givenHttpClientReturnsResponses(503, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture = httpAdapterConnector.call(adapter, usersyncer,
                adapterRequest,
                preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError())
                .isEqualTo(BidderError.badServerResponse("HTTP status 503; body: response"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("HTTP status 503; body: response");
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfHttpResponseStatusCodeIs400() {
        // given
        givenHttpClientReturnsResponses(400, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture = httpAdapterConnector.call(adapter, usersyncer,
                adapterRequest,
                preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError())
                .isEqualTo(BidderError.badInput("HTTP status 400; body: response"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("HTTP status 400; body: response");
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfHttpResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponses(200, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNotNull();
        assertThat(adapterResponse.getError().getMessage()).startsWith("Failed to decode");
        assertThat(adapterResponse.getError().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(adapterResponse.getBidderStatus().getError()).startsWith("Failed to decode");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void callShouldUnmarshalBodyToAdapterResponseClass() throws JsonProcessingException {
        // given
        adapterRequest = AdapterRequest.of("bidderCode1", singletonList(givenAdUnitBid(identity())));

        final Adapter<String, Object> anotherAdapter = (Adapter<String, Object>) mock(Adapter.class);
        willReturn(singletonList(givenHttpRequest())).given(anotherAdapter).makeHttpRequests(any(), any());
        willReturn(new TypeReference<CustomResponse>() {
        }).given(anotherAdapter).responseTypeReference();
        given(anotherAdapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()));

        final String bidResponse = mapper.writeValueAsString(CustomResponse.of("url", BigDecimal.ONE));
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(bidResponse)))
                .willReturn(httpClientResponse);

        // when
        httpAdapterConnector.call(anotherAdapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final ArgumentCaptor<ExchangeCall<String, Object>> exchangeCallCaptor =
                ArgumentCaptor.forClass(ExchangeCall.class);
        verify(anotherAdapter).extractBids(any(), exchangeCallCaptor.capture());
        assertThat(exchangeCallCaptor.getValue().getResponse()).isInstanceOf(CustomResponse.class);
    }

    @Test
    public void callShouldReturnBidderResultWithoutErrorIfBidsArePresent() throws JsonProcessingException {
        // given
        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()));

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(bidResponse)))
                .willReturn(httpClientResponse);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNull();
        assertThat(adapterResponse.getBidderStatus().getError()).isNull();
        assertThat(adapterResponse.getBids()).hasSize(1);
    }

    @Test
    public void
    callShouldReturnAdapterResponseWithErrorIfAtLeastOneErrorOccursWhileHttpRequestForNotToleratedErrorsAdapter()
            throws JsonProcessingException {
        // given
        willReturn(asList(givenHttpRequest(), givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());

        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()));

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(200);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(bidResponse)))
                .willReturn(httpClientResponse);

        final HttpClientResponse httpClientResponseWithError = mock(HttpClientResponse.class);
        given(httpClientResponseWithError.statusCode()).willReturn(503);
        given(httpClientResponseWithError.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer("error response")))
                .willReturn(httpClientResponseWithError);

        given(httpClient.requestAbs(any(), anyString(), any()))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponseWithError));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNotNull();
        assertThat(adapterResponse.getError().getMessage()).startsWith("HTTP status 503; body:");
        assertThat(adapterResponse.getError().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(adapterResponse.getBidderStatus().getError()).startsWith("HTTP status 503; body:");
    }

    @Test
    public void
    callShouldReturnAdapterResponseWithoutErrorIfAtLeastOneBidIsPresentWhileHttpRequestForToleratedErrorsAdapter()
            throws JsonProcessingException {
        // given
        willReturn(asList(givenHttpRequest(), givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());

        given(adapter.tolerateErrors()).willReturn(true);

        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()))
                .willReturn(null);

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(200);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(bidResponse)))
                .willReturn(httpClientResponse);

        final HttpClientResponse httpClientResponseWithError = mock(HttpClientResponse.class);
        given(httpClientResponseWithError.statusCode()).willReturn(503);
        given(httpClientResponseWithError.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer("error response")))
                .willReturn(httpClientResponseWithError);

        given(httpClient.requestAbs(any(), anyString(), any()))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponseWithError));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNull();
        assertThat(adapterResponse.getBidderStatus().getError()).isNull();
        assertThat(adapterResponse.getBids()).hasSize(1);
    }

    @Test
    public void
    callShouldReturnAdapterResponseWithErrorIfAtLeastOneErrorOccursWhileExtractingForNotToleratedErrorsAdapter()
            throws JsonProcessingException {
        // given
        willReturn(asList(givenHttpRequest(), givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());

        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()))
                .willThrow(new PreBidException("adapter extractBids exception"));

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        givenHttpClientReturnsResponses(200, bidResponse, bidResponse);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError())
                .isEqualTo(BidderError.badServerResponse("adapter extractBids exception"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("adapter extractBids exception");
    }

    @Test
    public void
    callShouldReturnAdapterResponseWithoutErrorIfAtLeastOneBidIsPresentWhileExtractingForToleratedErrorsAdapter()
            throws JsonProcessingException {
        // given
        willReturn(asList(givenHttpRequest(), givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());

        given(adapter.tolerateErrors()).willReturn(true);

        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()))
                .willThrow(new PreBidException("adapter extractBids exception"));

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        givenHttpClientReturnsResponses(200, bidResponse, bidResponse);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNull();
        assertThat(adapterResponse.getBidderStatus().getError()).isNull();
        assertThat(adapterResponse.getBids()).hasSize(1);
    }

    @Test
    public void callShouldReturnAdapterResponseWithEmptyBidsIfAdUnitBidIsBannerAndSizesLengthMoreThanOne()
            throws JsonProcessingException {
        // given
        adapterRequest = AdapterRequest.of("bidderCode1", singletonList(
                givenAdUnitBid(builder -> builder
                        .adUnitCode("adUnitCode1")
                        .sizes(asList(Format.builder().w(100).h(200).build(), Format.builder().w(100).h(200).build()))
                        .bidId("bidId1"))));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()
                        .code("adUnitCode1")
                        .bidId("bidId1")
                        .mediaType(MediaType.banner)));

        givenHttpClientReturnsResponses(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBids()).isEmpty();
        assertThat(adapterResponse.getBidderStatus().getNumBids()).isEqualTo(0);
    }

    @Test
    public void callShouldReturnAdapterResponseWithNoCookieIfNoAdapterUidInCookieAndNoAppInPreBidRequest()
            throws IOException {
        // given
        givenHttpClientReturnsResponses(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));

        given(usersyncer.usersyncInfo()).willReturn(UsersyncInfo.of("url1", null, false));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getNoCookie()).isTrue();
        assertThat(adapterResponse.getBidderStatus().getUsersync()).isNotNull();
        assertThat(adapterResponse.getBidderStatus().getUsersync()).isEqualTo(UsersyncInfo.of("url1", null, false));
    }

    @Test
    public void callShouldReturnAdapterResponseWithoutNoCookieIfNoAdapterUidInCookieAndAppPresentInPreBidRequest()
            throws IOException {
        // given
        preBidRequestContext = givenPreBidRequestContext(identity(),
                builder -> builder.app(App.builder().build()).user(User.builder().build()));

        givenHttpClientReturnsResponses(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getNoCookie()).isNull();
        assertThat(adapterResponse.getBidderStatus().getUsersync()).isNull();
    }

    @Test
    public void callShouldReturnAdapterResponseWithDebugIfFlagIsTrue() throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContext(builder -> builder.isDebug(true), identity());

        adapterRequest = AdapterRequest.of("bidderCode1", asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2"))));

        final String bidResponse = givenBidResponse(builder -> builder.id("bidResponseId1"),
                identity(),
                asList(bidBuilder -> bidBuilder.impid("adUnitCode1"), bidBuilder -> bidBuilder.impid("adUnitCode2")));
        givenHttpClientReturnsResponses(200, bidResponse);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();

        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest).end(bidRequestCaptor.capture());
        final List<String> bidRequests = bidRequestCaptor.getAllValues();

        assertThat(adapterResponse.getBidderStatus().getDebug()).hasSize(1).containsOnly(
                BidderDebug.builder()
                        .requestUri("uri")
                        .requestBody(bidRequests.get(0))
                        .responseBody(bidResponse)
                        .statusCode(200)
                        .build());
    }

    @Test
    public void callShouldReturnAdapterResponseWithoutDebugIfFlagIsFalse() throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContext(builder -> builder.isDebug(false), identity());

        givenHttpClientReturnsResponses(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        assertThat(adapterResponseFuture.result().getBidderStatus().getDebug()).isNull();
    }

    @Test
    public void callShouldReturnAdapterResponseWithDebugIfFlagIsTrueAndGlobalTimeoutAlreadyExpired() {
        // given
        preBidRequestContext = givenPreBidRequestContext(
                builder -> builder
                        .timeout(expiredTimeout())
                        .isDebug(true),
                identity());

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getDebug()).hasSize(1);

        final BidderDebug bidderDebug = adapterResponse.getBidderStatus().getDebug().get(0);
        assertThat(bidderDebug.getRequestUri()).isNotBlank();
        assertThat(bidderDebug.getRequestBody()).isNotBlank();
    }

    @Test
    public void callShouldReturnAdapterResponseWithDebugIfFlagIsTrueAndHttpRequestFails() {
        // given
        preBidRequestContext = givenPreBidRequestContext(builder -> builder.isDebug(true), identity());

        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getDebug()).hasSize(1);

        final BidderDebug bidderDebug = adapterResponse.getBidderStatus().getDebug().get(0);
        assertThat(bidderDebug.getRequestUri()).isNotBlank();
        assertThat(bidderDebug.getRequestBody()).isNotBlank();
    }

    @Test
    public void callShouldReturnAdapterResponseWithDebugIfFlagIsTrueAndResponseIsNotSuccessful() {
        // given
        preBidRequestContext = givenPreBidRequestContext(builder -> builder.isDebug(true), identity());

        givenHttpClientReturnsResponses(503, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getDebug()).hasSize(1);

        final BidderDebug bidderDebug = adapterResponse.getBidderStatus().getDebug().get(0);
        assertThat(bidderDebug.getRequestUri()).isNotBlank();
        assertThat(bidderDebug.getRequestBody()).isNotBlank();
        assertThat(bidderDebug.getResponseBody()).isNotBlank();
        assertThat(bidderDebug.getStatusCode()).isPositive();
    }

    private BidRequest captureBidRequest() throws IOException {
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest).end(bidRequestCaptor.capture());
        return mapper.readValue(bidRequestCaptor.getValue(), BidRequest.class);
    }

    private AdapterHttpRequest<Object> givenHttpRequest(HttpMethod method) {
        return AdapterHttpRequest.of(method, "uri", null, null);
    }

    private static AdapterHttpRequest<BidRequest> givenHttpRequest() {
        return AdapterHttpRequest.of(POST, "uri", givenBidRequest(identity()), null);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder()).build();
    }

    private static AdUnitBid givenAdUnitBid(Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer) {
        final AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .adUnitCode("adUnitCode1")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .mediaTypes(singleton(MediaType.banner));
        return adUnitBidBuilderCustomizer.apply(adUnitBidBuilderMinimal).build();
    }

    private PreBidRequestContext givenPreBidRequestContext(
            Function<PreBidRequestContextBuilder, PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer,
            Function<PreBidRequestBuilder, PreBidRequestBuilder> preBidRequestBuilderCustomizer) {

        final PreBidRequestBuilder preBidRequestBuilderMinimal = PreBidRequest.builder()
                .accountId("accountId");
        final PreBidRequest preBidRequest = preBidRequestBuilderCustomizer.apply(preBidRequestBuilderMinimal).build();

        final PreBidRequestContextBuilder preBidRequestContextBuilderMinimal =
                PreBidRequestContext.builder()
                        .preBidRequest(preBidRequest)
                        .uidsCookie(uidsCookie)
                        .timeout(timeout());
        return preBidRequestContextBuilderCustomizer.apply(preBidRequestContextBuilderMinimal).build();
    }

    private static String givenBidResponse(
            Function<BidResponseBuilder, BidResponseBuilder> bidResponseBuilderCustomizer,
            Function<SeatBidBuilder, SeatBidBuilder> seatBidBuilderCustomizer,
            List<Function<BidBuilder, BidBuilder>> bidBuilderCustomizers) throws JsonProcessingException {

        // bid
        final BidBuilder bidBuilderMinimal = com.iab.openrtb.response.Bid.builder();
        final List<Bid> bids = bidBuilderCustomizers.stream()
                .map(bidBuilderBidBuilderFunction -> bidBuilderBidBuilderFunction.apply(bidBuilderMinimal).build())
                .collect(Collectors.toList());

        // seatBid
        final SeatBidBuilder seatBidBuilderMinimal = SeatBid.builder().bid(bids);
        final SeatBid seatBid = seatBidBuilderCustomizer.apply(seatBidBuilderMinimal).build();

        // bidResponse
        final BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder()
                .seatbid(singletonList(seatBid));
        final BidResponse bidResponse = bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal).build();

        return mapper.writeValueAsString(bidResponse);
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);
        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willAnswer(withSelfAndPassObjectToHandler(throwable));
    }

    private void givenHttpClientReturnsResponses(int statusCode, String... bidResponses) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(statusCode);

        // setup multiple answers
        BDDMockito.BDDMyOngoingStubbing<HttpClientResponse> currentStubbing =
                given(httpClientResponse.bodyHandler(any()));
        for (String bidResponse : bidResponses) {
            currentStubbing = currentStubbing.willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(bidResponse)));
        }
    }

    private HttpClientResponse givenHttpClientResponse(int statusCode) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClient.requestAbs(any(), anyString(), any()))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse));
        given(httpClientResponse.statusCode()).willReturn(statusCode);
        return httpClientResponse;
    }

    @SuppressWarnings("unchecked")
    private Answer<Object> withRequestAndPassResponseToHandler(HttpClientResponse httpClientResponse) {
        return inv -> {
            // invoking passed HttpClientResponse handler right away passing mock response to it
            ((Handler<HttpClientResponse>) inv.getArgument(2)).handle(httpClientResponse);
            return httpClientRequest;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }

    private Timeout timeout() {
        return new TimeoutFactory(clock).create(500L);
    }

    private Timeout expiredTimeout() {
        return new TimeoutFactory(clock).create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class CustomResponse {

        String url;

        BigDecimal price;
    }
}
