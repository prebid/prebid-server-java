package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.vertx.http.HttpClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class HttpBidderRequesterTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Bidder<?> bidder;
    @Mock
    private HttpClient httpClient;

    private HttpBidderRequester<?> bidderHttpConnector;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        bidderHttpConnector = new HttpBidderRequester<>(bidder, httpClient);
    }

    @Test
    public void shouldReturnFailedToRequestBidsErrorWhenBidderReturnsEmptyHttpRequestAndErrorLists() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(emptyList(), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid = bidderHttpConnector.requestBids(BidRequest.builder().build(), timeout)
                .result();

        // then
        assertThat(bidderSeatBid.getBids()).isEmpty();
        assertThat(bidderSeatBid.getHttpCalls()).isEmpty();
        assertThat(bidderSeatBid.getErrors())
                .containsOnly(BidderError.failedToRequestBids("The bidder failed to generate any bid " +
                        "requests, but also failed to generate an error"));
    }

    @Test
    public void shouldTolerateBidderReturningErrorsAndNoHttpRequests() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(emptyList(),
                asList(BidderError.badInput("error1"), BidderError.badInput("error2"))));

        // when
        final BidderSeatBid bidderSeatBid = bidderHttpConnector.requestBids(BidRequest.builder().build(), timeout)
                .result();

        // then
        assertThat(bidderSeatBid.getBids()).isEmpty();
        assertThat(bidderSeatBid.getHttpCalls()).isEmpty();
        assertThat(bidderSeatBid.getErrors())
                .extracting(BidderError::getMessage).containsOnly("error1", "error2");
    }

    @Test
    public void shouldSendPopulatedPostRequest() {
        // given
        givenHttpClientResponse(200);

        final MultiMap headers = new CaseInsensitiveHeaders();
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, "uri", "requestBody", headers, null)), emptyList()));
        headers.add("header1", "value1");
        headers.add("header2", "value2");

        // when
        bidderHttpConnector.requestBids(BidRequest.builder().build(), timeout);

        // then
        verify(httpClient).request(eq(HttpMethod.POST), eq("uri"), eq(headers), eq("requestBody"), eq(500L));
    }

    @Test
    public void shouldSendPopulatedGetRequestWithoutBody() {
        // given
        givenHttpClientResponse(200);

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.GET, "uri", null, null, null)), emptyList()));

        // when
        bidderHttpConnector.requestBids(BidRequest.builder().build(), timeout);

        // then
        verify(httpClient).request(any(), anyString(), any(), isNull(), anyLong());
    }

    @Test
    public void shouldSendMultipleRequests() {
        // given
        givenHttpClientResponse(200);

        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null),
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null)),
                emptyList()));

        // when
        bidderHttpConnector.requestBids(BidRequest.builder().build(), timeout);

        // then
        verify(httpClient, times(2)).request(any(), anyString(), any(), any(), anyLong());
    }

    @Test
    public void shouldReturnBidsCreatedByBidder() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null)), emptyList()));

        givenHttpClientReturnsResponses(200, "responseBody");

        final List<BidderBid> bids = asList(BidderBid.of(null, null, null), BidderBid.of(null, null, null));
        given(bidder.makeBids(any(), any())).willReturn(Result.of(bids, emptyList()));

        // when
        final BidderSeatBid bidderSeatBid = bidderHttpConnector.requestBids(BidRequest.builder().build(), timeout)
                .result();

        // then
        assertThat(bidderSeatBid.getBids()).containsOnlyElementsOf(bids);
    }

    @Test
    public void shouldReturnFullDebugInfoIfTestFlagIsOn() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                HttpRequest.of(HttpMethod.POST, "uri1", "requestBody1", new CaseInsensitiveHeaders(), null),
                HttpRequest.of(HttpMethod.POST, "uri2", "requestBody2", new CaseInsensitiveHeaders(), null)),
                emptyList()));

        givenHttpClientReturnsResponses(200, "responseBody1", "responseBody2");

        given(bidder.makeBids(any(), any())).willReturn(Result.of(emptyList(), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid =
                bidderHttpConnector.requestBids(BidRequest.builder().test(1).build(), timeout).result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).hasSize(2).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody("requestBody1").responsebody("responseBody1")
                        .status(200).build(),
                ExtHttpCall.builder().uri("uri2").requestbody("requestBody2").responsebody("responseBody2")
                        .status(200).build());
    }

    @Test
    public void shouldReturnPartialDebugInfoIfTestFlagIsOnAndGlobalTimeoutAlreadyExpired() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, "uri1", "requestBody1", new CaseInsensitiveHeaders(), null)),
                emptyList()));

        // when
        final BidderSeatBid bidderSeatBid =
                bidderHttpConnector.requestBids(BidRequest.builder().test(1).build(), expiredTimeout).result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).hasSize(1).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody("requestBody1").build());
    }

    @Test
    public void shouldReturnPartialDebugInfoIfTestFlagIsOnAndHttpErrorOccurs() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, "uri1", "requestBody1", new CaseInsensitiveHeaders(), null)),
                emptyList()));

        givenHttpClientProducesException(new RuntimeException("Request exception"));

        // when
        final BidderSeatBid bidderSeatBid =
                bidderHttpConnector.requestBids(BidRequest.builder().test(1).build(), timeout).result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).hasSize(1).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody("requestBody1").build());
    }

    @Test
    public void shouldReturnFullDebugInfoIfTestFlagIsOnAndErrorStatus() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, "uri1", "requestBody1", new CaseInsensitiveHeaders(), null)),
                emptyList()));

        givenHttpClientReturnsResponses(500, "responseBody1");

        // when
        final BidderSeatBid bidderSeatBid =
                bidderHttpConnector.requestBids(BidRequest.builder().test(1).build(), timeout).result();

        // then
        assertThat(bidderSeatBid.getHttpCalls()).hasSize(1).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody("requestBody1").responsebody("responseBody1")
                        .status(500).build());
        assertThat(bidderSeatBid.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage).containsOnly(
                "Unexpected status code: 500. Run with request.test = 1 for more info");
    }

    @Test
    public void shouldTolerateAlreadyExpiredGlobalTimeout() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null)), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid =
                bidderHttpConnector.requestBids(BidRequest.builder().build(), expiredTimeout).result();

        // then
        assertThat(bidderSeatBid.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsOnly("Timeout has been exceeded");
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void shouldTolerateMultipleErrors() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                // this request will fail with response exception
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null),
                // this request will fail with timeout
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null),
                // this request will fail with 500 status
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null),
                // this request will fail with 400 status
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null),
                // this request will get 204 status
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null),
                // finally this request will succeed
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders(), null)),
                singletonList(BidderError.badInput("makeHttpRequestsError"))));

        final HttpClientResponse httpClientResponse = givenHttpClientResponse(0);

        given(httpClientResponse.exceptionHandler(any()))
                // simulate response error for the first request
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Response exception")))
                // simulate timeout for the second request (which will trigger exceptionHandler call on
                // response mock first time)
                .willAnswer(withSelfAndPassObjectToHandler(new TimeoutException("Timeout exception")))
                // continue normally for subsequent requests
                .willReturn(httpClientResponse);
        given(httpClientResponse.bodyHandler(any()))
                // do not invoke body handler for the second and third requests (which will trigger bodyHandler call on
                // response mock first and second time) that will end up with response error and timeout
                .willReturn(httpClientResponse)
                .willReturn(httpClientResponse)
                // continue normally for subsequent requests
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(EMPTY)));
        given(httpClientResponse.statusCode())
                // simulate 500 status for the fourth request (which will trigger statusCode call on response mock
                // first time)
                .willReturn(500)
                // simulate 400 status for the fifth request (which will trigger statusCode call on response mock
                // second time)
                .willReturn(400)
                // simulate 204 status for the sixth request (which will trigger statusCode call on response mock
                // third time)
                .willReturn(204)
                // continue normally for subsequent requests
                .willReturn(200);

        given(bidder.makeBids(any(), any())).willReturn(
                Result.of(singletonList(BidderBid.of(null, null, null)),
                        singletonList(BidderError.badServerResponse("makeBidsError"))));

        // when
        final BidderSeatBid bidderSeatBid = bidderHttpConnector
                .requestBids(BidRequest.builder().test(1).build(), timeout)
                .result();

        // then
        // only one call is expected since other requests failed with errors or returned with 204 status
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

    private void givenHttpClientReturnsResponses(int statusCode, String... bidResponses) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(statusCode);

        // setup multiple answers
        BDDMockito.BDDMyOngoingStubbing<HttpClientResponse> stubbing =
                given(httpClientResponse.bodyHandler(any()));
        for (String bidResponse : bidResponses) {
            stubbing = stubbing.willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(bidResponse)));
        }
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);
        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willAnswer(withSelfAndPassObjectToHandler(throwable));
    }

    private HttpClientResponse givenHttpClientResponse(int statusCode) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(statusCode);

        given(httpClient.request(any(), anyString(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));

        return httpClientResponse;
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            // invoking handler right away passing mock to it
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }
}
