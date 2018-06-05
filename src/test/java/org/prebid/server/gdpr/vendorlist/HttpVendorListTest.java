package org.prebid.server.gdpr.vendorlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.vendorlist.proto.VendorListInfo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class HttpVendorListTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpClientRequest httpClientRequest;

    private HttpVendorList httpVendorList;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
        given(httpClient.getAbs(anyString(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        httpVendorList = new HttpVendorList(httpClient, "http://vendorlist/%s");
    }

    @Test
    public void shouldFailByTimeoutIfExceeded() {
        // when
        final Future<?> future = httpVendorList.forVersion(1, expiredTimeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessage("Error fetching vendor list via HTTP for version 1 with error: Timeout has been exceeded");
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void shouldRequestVendorListWithExpectedUrl() {
        // when
        httpVendorList.forVersion(1, timeout);

        // then
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).getAbs(captor.capture(), any());
        assertThat(captor.getValue()).isEqualTo("http://vendorlist/1");
    }

    @Test
    public void shouldFailIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<?> future = httpVendorList.forVersion(1, timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessage("Error fetching vendor list via HTTP for version 1 with error: Request exception");
    }

    @Test
    public void shouldFailIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<?> future = httpVendorList.forVersion(1, timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessage("Error fetching vendor list via HTTP for version 1 with error: Response exception");
    }

    @Test
    public void shouldFailIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<?> future = httpVendorList.forVersion(1, timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessage("Error fetching vendor list via HTTP for version 1 with error: response code was 503");
    }

    @Test
    public void shouldFailIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        final Future<?> future = httpVendorList.forVersion(1, timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessageStartingWith("Error fetching vendor list via HTTP for version 1 with error: "
                        + "parsing json failed for response: response with message: Failed to decode");
    }

    @Test
    public void shouldFailIfFetchedVendorListHasInvalidVendorListVersion() throws JsonProcessingException {
        // given
        final VendorListInfo vendorList = VendorListInfo.of(0, null, null);
        final String response = mapper.writeValueAsString(vendorList);
        givenHttpClientReturnsResponse(200, response);

        // when
        final Future<?> future = httpVendorList.forVersion(1, timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessageStartingWith("Error fetching vendor list via HTTP for version 1 with error: "
                        + "fetched vendor list parsed but has invalid data");
    }

    @Test
    public void shouldFailIfFetchedVendorListHasInvalidLastUpdated() throws JsonProcessingException {
        // given
        final VendorListInfo vendorList = VendorListInfo.of(1, null, null);
        final String response = mapper.writeValueAsString(vendorList);
        givenHttpClientReturnsResponse(200, response);

        // when
        final Future<?> future = httpVendorList.forVersion(1, timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessageStartingWith("Error fetching vendor list via HTTP for version 1 with error: "
                        + "fetched vendor list parsed but has invalid data");
    }

    @Test
    public void shouldFailIfFetchedVendorListHasInvalidVendors() throws JsonProcessingException {
        // given
        final VendorListInfo vendorList = VendorListInfo.of(1, new Date(), null);
        final String response = mapper.writeValueAsString(vendorList);
        givenHttpClientReturnsResponse(200, response);

        // when
        final Future<?> future = httpVendorList.forVersion(1, timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessageStartingWith("Error fetching vendor list via HTTP for version 1 with error: "
                        + "fetched vendor list parsed but has invalid data");
    }

    @Test
    public void shouldFailIfFetchedVendorListHasEmptyVendors() throws JsonProcessingException {
        // given
        final VendorListInfo vendorList = VendorListInfo.of(1, new Date(), emptyList());
        final String response = mapper.writeValueAsString(vendorList);
        givenHttpClientReturnsResponse(200, response);

        // when
        final Future<?> future = httpVendorList.forVersion(1, timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessageStartingWith("Error fetching vendor list via HTTP for version 1 with error: "
                        + "fetched vendor list parsed but has invalid data");
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(statusCode);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(response)));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);

        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willAnswer(withSelfAndPassObjectToHandler(throwable));
    }

    private HttpClientResponse givenHttpClientResponse(int statusCode) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClient.getAbs(anyString(), any()))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse));
        given(httpClientResponse.statusCode()).willReturn(statusCode);
        return httpClientResponse;
    }

    @SuppressWarnings("unchecked")
    private Answer<Object> withRequestAndPassResponseToHandler(HttpClientResponse httpClientResponse) {
        return inv -> {
            // invoking passed HttpClientResponse handler right away passing mock response to it
            ((Handler<HttpClientResponse>) inv.getArgument(1)).handle(httpClientResponse);
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
