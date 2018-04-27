package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
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
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.HttpFetcherResponse;
import org.prebid.server.settings.model.StoredRequestResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class HttpApplicationSettingsTest extends VertxTest {

    private static final String ENDPOINT = "http://stored-requests";
    private static final String AMP_ENDPOINT = "http://amp-stored-requests";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private HttpApplicationSettings httpApplicationSettings;

    @Mock
    private HttpClientRequest httpClientRequest;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
        given(httpClient.getAbs(anyString(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

        httpApplicationSettings = new HttpApplicationSettings(httpClient, ENDPOINT, AMP_ENDPOINT);

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);
    }

    @Test
    public void creationShouldFailsOnInvalidEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpApplicationSettings(httpClient, "invalid_url", AMP_ENDPOINT))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldFailsOnInvalidAmpEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpApplicationSettings(httpClient, ENDPOINT, "invalid_url"))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void getAccountByIdShouldReturnEmptyResult() {
        // when
        final Future<Account> future = httpApplicationSettings.getAccountById(null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("Not supported");
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnEmptyResult() {
        // when
        final Future<String> future = httpApplicationSettings.getAdUnitConfigById(null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("Not supported");
    }

    @Test
    public void getStoredRequestsByIdShouldReturnEmptyResultIfEmptyRequestsIdsGiven() {
        // when
        final Future<StoredRequestResult> future = httpApplicationSettings.getStoredRequestsById(emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToJson()).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void getStoredRequestsByIdShouldReturnResultWithErrorIfTimeoutAlreadyExpired() {
        // when
        final Future<StoredRequestResult> future = httpApplicationSettings.getStoredRequestsById(singleton("id1"),
                expiredTimeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToJson()).isEmpty();
        assertThat(future.result().getErrors())
                .containsOnly("Error fetching stored requests for ids [id1] via HTTP: Timeout has been exceeded");
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void getStoredRequestsByIdShouldSendHttpRequestWithExpectedNewParams() {
        // when
        httpApplicationSettings.getStoredRequestsById(new HashSet<>(asList("id1", "id2")), timeout);

        // then
        final ArgumentCaptor<String> timeoutCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).getAbs(timeoutCaptor.capture(), any());
        assertThat(timeoutCaptor.getValue()).isEqualTo("http://stored-requests?request-ids=id2,id1");
    }

    @Test
    public void getStoredRequestsByIdShouldSendHttpRequestWithExpectedAppendedParams() {
        // given
        httpApplicationSettings = new HttpApplicationSettings(httpClient, "http://some-domain?param1=value1",
                AMP_ENDPOINT);

        // when
        httpApplicationSettings.getStoredRequestsById(singleton("id1"), timeout);

        // then
        final ArgumentCaptor<String> timeoutCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).getAbs(timeoutCaptor.capture(), any());
        assertThat(timeoutCaptor.getValue()).isEqualTo("http://some-domain?param1=value1&request-ids=id1");
    }

    @Test
    public void getStoredRequestsByIdShouldReturnResultWithErrorIfHttpClientFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Request exception"));

        // when
        final Future<StoredRequestResult> future =
                httpApplicationSettings.getStoredRequestsById(singleton("id1"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToJson()).isEmpty();
        assertThat(future.result().getErrors())
                .containsOnly("Error fetching stored requests for ids [id1] via HTTP: Request exception");
    }

    @Test
    public void getStoredRequestsByIdShouldReturnResultWithErrorIfHttpClientRespondsNot200Status() {
        // given
        givenHttpClientReturnsResponses(500, "ignored");

        // when
        final Future<StoredRequestResult> future =
                httpApplicationSettings.getStoredRequestsById(singleton("id1"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToJson()).isEmpty();
        assertThat(future.result().getErrors())
                .containsOnly("Error fetching stored requests for ids [id1] via HTTP: Response code was 500");
    }

    @Test
    public void getStoredRequestsByIdShouldReturnResultWithErrorIfHttpResponseIsMalformed() {
        // given
        givenHttpClientReturnsResponses(200, "invalid-response");

        // when
        final Future<StoredRequestResult> future =
                httpApplicationSettings.getStoredRequestsById(singleton("id1"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToJson()).isEmpty();
        assertThat(future.result().getErrors().get(0))
                .startsWith("Error occurred while parsing stored requests for ids [id1] from response: invalid-response"
                        + " with message: Failed to decode");
    }

    @Test
    public void getStoredRequestsByIdShouldReturnResultWithErrorIfStoredRequestObjectIsMalformed() {
        // given
        final String malformedStoredRequest = "{\"requests\": {\"id1\":\"invalid-stored-request\"}";
        givenHttpClientReturnsResponses(200, malformedStoredRequest);

        // when
        final Future<StoredRequestResult> future =
                httpApplicationSettings.getStoredRequestsById(singleton("id1"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToJson()).isEmpty();
        assertThat(future.result().getErrors().get(0))
                .startsWith("Error occurred while parsing stored requests for ids [id1] from response: "
                        + "{\"requests\": {\"id1\":\"invalid-stored-request\"} with message: Failed to decode");
    }

    @Test
    public void getStoredRequestsByIdShouldTolerateMissedId() throws JsonProcessingException {
        // given
        final HttpFetcherResponse response = HttpFetcherResponse.of(singletonMap("id1", mapper.createObjectNode()));
        givenHttpClientReturnsResponses(200, mapper.writeValueAsString(response));

        // when
        final Future<StoredRequestResult> future =
                httpApplicationSettings.getStoredRequestsById(new HashSet<>(asList("id1", "id2")), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToJson().entrySet()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("id1", "{}"));
        assertThat(future.result().getErrors())
                .containsOnly("No config found for id: id2");
    }

    @Test
    public void getStoredRequestsByIdShouldReturnExpectedResult() throws JsonProcessingException {
        // given
        final ObjectNode objectNode = mapper.createObjectNode().put("field1", "field-value1");
        final HttpFetcherResponse response = HttpFetcherResponse.of(singletonMap("id1", objectNode));
        givenHttpClientReturnsResponses(200, mapper.writeValueAsString(response));

        // when
        final Future<StoredRequestResult> future =
                httpApplicationSettings.getStoredRequestsById(singleton("id1"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToJson().entrySet()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("id1", "{\"field1\":\"field-value1\"}"));
    }

    private void givenHttpClientReturnsResponses(int statusCode, String response) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(statusCode);

        // setup multiple answers
        final BDDMockito.BDDMyOngoingStubbing<HttpClientResponse> currentStubbing =
                given(httpClientResponse.bodyHandler(any()));

        currentStubbing.willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(response)));
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
