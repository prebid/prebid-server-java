package org.rtb.vexing.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
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
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.BidderSeatBid;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.Result;
import org.rtb.vexing.model.openrtb.ext.response.ExtHttpCall;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

public class HttpConnectorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpClientRequest httpClientRequest;

    private HttpConnector httpConnector;

    @Mock
    private Bidder bidder;

    @Before
    public void setUp() {
        // given
        given(httpClient.requestAbs(any(), anyString(), any())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);
        given(httpClientRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        httpConnector = new HttpConnector(httpClient);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new HttpConnector(null));
    }

    @Test
    public void shouldTolerateBidderReturningNoHttpRequests() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(emptyList(), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid = httpConnector.requestBids(bidder, BidRequest.builder().build()).result();

        // then
        assertThat(bidderSeatBid.bids).hasSize(0);
        assertThat(bidderSeatBid.httpCalls).hasSize(0);
        assertThat(bidderSeatBid.errors).hasSize(0);
        assertThat(bidderSeatBid.ext).isNull();
    }

    @Test
    public void shouldTolerateBidderReturningErrorsAndNoHttpRequests() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(emptyList(), asList("error1", "error2")));

        // when
        final BidderSeatBid bidderSeatBid = httpConnector.requestBids(bidder, BidRequest.builder().build()).result();

        // then
        assertThat(bidderSeatBid.bids).hasSize(0);
        assertThat(bidderSeatBid.httpCalls).hasSize(0);
        assertThat(bidderSeatBid.errors).containsOnly("error1", "error2");
    }

    @Test
    public void shouldSendPopulatedPostRequest() {
        // given
        final MultiMap headers = new CaseInsensitiveHeaders();
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, "uri", "requestBody", headers)), emptyList()));
        headers.add("header1", "value1");
        headers.add("header2", "value2");

        // when
        httpConnector.requestBids(bidder, BidRequest.builder().tmax(600L).build());

        // then
        verify(httpClient).requestAbs(eq(HttpMethod.POST), eq("uri"), any());
        verify(httpClientRequest).setTimeout(eq(600L));
        verify(httpClientRequest).end(eq("requestBody"));
        assertThat(httpClientRequest.headers()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("header1", "value1"), tuple("header2", "value2"));
    }

    @Test
    public void shouldSendRequestWithoutTimeoutIfNoTmax() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders())), emptyList()));

        // when
        httpConnector.requestBids(bidder, BidRequest.builder().build());

        // then
        verify(httpClientRequest, never()).setTimeout(anyLong());
    }

    @Test
    public void shouldSendRequestWithoutTimeoutIfTmaxIsNotPositive() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders())), emptyList()));

        // when
        httpConnector.requestBids(bidder, BidRequest.builder().tmax(0L).build());

        // then
        verify(httpClientRequest, never()).setTimeout(anyLong());
    }

    @Test
    public void shouldSendMultipleRequests() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders()),
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders())),
                emptyList()));

        // when
        httpConnector.requestBids(bidder, BidRequest.builder().build());

        // then
        verify(httpClient, times(2)).requestAbs(any(), any(), any());
    }

    @Test
    public void shouldReturnBidsCreatedByBidder() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders())), emptyList()));

        givenHttpClientReturnsResponses(200, "responseBody");

        final List<BidderBid> bids = asList(BidderBid.of(null, null), BidderBid.of(null, null));
        given(bidder.makeBids(any())).willReturn(Result.of(bids, emptyList()));

        // when
        final BidderSeatBid bidderSeatBid = httpConnector.requestBids(bidder, BidRequest.builder().build()).result();

        // then
        assertThat(bidderSeatBid.bids).containsOnlyElementsOf(bids);
    }

    @Test
    public void shouldReturnFullDebugInfoIfTestFlagIsOn() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                HttpRequest.of(HttpMethod.POST, "uri1", "requestBody1", new CaseInsensitiveHeaders()),
                HttpRequest.of(HttpMethod.POST, "uri2", "requestBody2", new CaseInsensitiveHeaders())),
                emptyList()));

        givenHttpClientReturnsResponses(200, "responseBody1", "responseBody2");

        given(bidder.makeBids(any())).willReturn(Result.of(emptyList(), emptyList()));

        // when
        final BidderSeatBid bidderSeatBid = httpConnector.requestBids(bidder, BidRequest.builder().test(1).build())
                .result();

        // then
        assertThat(bidderSeatBid.httpCalls).hasSize(2).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody("requestBody1").responsebody("responseBody1")
                        .status(200).build(),
                ExtHttpCall.builder().uri("uri2").requestbody("requestBody2").responsebody("responseBody2")
                        .status(200).build());
    }

    @Test
    public void shouldReturnPartialDebugInfoIfTestFlagIsOnAndHttpErrorOccurs() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, "uri1", "requestBody1", new CaseInsensitiveHeaders())),
                emptyList()));

        givenHttpClientProducesException(new RuntimeException("Request exception"));

        // when
        final BidderSeatBid bidderSeatBid = httpConnector.requestBids(bidder, BidRequest.builder().test(1).build())
                .result();

        // then
        assertThat(bidderSeatBid.httpCalls).hasSize(1).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody("requestBody1").build());
    }

    @Test
    public void shouldReturnFullDebugInfoIfTestFlagIsOnAndErrorStatus() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(singletonList(
                HttpRequest.of(HttpMethod.POST, "uri1", "requestBody1", new CaseInsensitiveHeaders())),
                emptyList()));

        givenHttpClientReturnsResponses(500, "responseBody1");

        // when
        final BidderSeatBid bidderSeatBid = httpConnector.requestBids(bidder, BidRequest.builder().test(1).build())
                .result();

        // then
        assertThat(bidderSeatBid.httpCalls).hasSize(1).containsOnly(
                ExtHttpCall.builder().uri("uri1").requestbody("requestBody1").responsebody("responseBody1")
                        .status(500).build());
        assertThat(bidderSeatBid.errors).hasSize(1).containsOnly(
                "Server responded with failure status: 500. Set request.test = 1 for debugging info.");
    }

    @Test
    public void shouldTolerateMultipleErrors() {
        // given
        given(bidder.makeHttpRequests(any())).willReturn(Result.of(asList(
                // this request will fail with request exception
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders()),
                // this request will fail with response exception
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders()),
                // this request will fail with 500 status
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders()),
                // finally this request will succeed
                HttpRequest.of(HttpMethod.POST, EMPTY, EMPTY, new CaseInsensitiveHeaders())),
                singletonList("makeHttpRequestsError")));

        given(httpClientRequest.exceptionHandler(any()))
                // simulate request error for the first request
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")))
                // continue normally for subsequent requests
                .willReturn(httpClientRequest);
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClient.requestAbs(any(), anyString(), any()))
                // do not invoke response handler for the first request that will end up with request error
                .willReturn(httpClientRequest)
                // continue normally for subsequent requests
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse));
        given(httpClientResponse.exceptionHandler(any()))
                // simulate response error for the second request (which will trigger exceptionHandler call on
                // response mock first time)
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Response exception")))
                // continue normally for subsequent requests
                .willReturn(httpClientResponse);
        given(httpClientResponse.bodyHandler(any()))
                // do not invoke body handler for the second request (which will trigger bodyHandler call on
                // response mock first time) that will end up with response error
                .willReturn(httpClientResponse)
                // continue normally for subsequent requests
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(EMPTY)));
        given(httpClientResponse.statusCode())
                // simulate 500 status for the third request (which will trigger statusCode call on response mock
                // first time)
                .willReturn(500)
                // continue normally for subsequent requests
                .willReturn(200);

        given(bidder.makeBids(any())).willReturn(
                Result.of(singletonList(BidderBid.of(null, null)), singletonList("makeBidsError")));

        // when
        final BidderSeatBid bidderSeatBid = httpConnector.requestBids(bidder, BidRequest.builder().test(1).build())
                .result();

        // then
        // only one call is expected since other requests failed with errors
        verify(bidder).makeBids(any());
        assertThat(bidderSeatBid.bids).hasSize(1);
        assertThat(bidderSeatBid.errors).hasSize(5).containsOnly(
                "makeHttpRequestsError",
                "Request exception",
                "Response exception",
                "Server responded with failure status: 500. Set request.test = 1 for debugging info.",
                "makeBidsError");
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

    private void givenHttpClientProducesException(Throwable throwable) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);
        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willAnswer(withSelfAndPassObjectToHandler(throwable));
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
}