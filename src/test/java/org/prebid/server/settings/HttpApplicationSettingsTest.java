package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
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
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.proto.response.HttpFetcherResponse;
import org.prebid.server.vertx.http.HttpClient;

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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;

public class HttpApplicationSettingsTest extends VertxTest {

    private static final String ENDPOINT = "http://stored-requests";
    private static final String AMP_ENDPOINT = "http://amp-stored-requests";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private HttpApplicationSettings httpApplicationSettings;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
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
    public void getStoredDataShouldReturnEmptyResultIfEmptyRequestsIdsGiven() {
        // when
        final Future<StoredDataResult> future = httpApplicationSettings.getStoredData(emptySet(), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getStoredIdToImp()).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfTimeoutAlreadyExpired() {
        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(singleton("id1"), emptySet(), expiredTimeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getStoredIdToImp()).isEmpty();
        assertThat(future.result().getErrors())
                .containsOnly("Error fetching stored requests for ids [id1] via HTTP: Timeout has been exceeded");
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void getStoredDataShouldSendHttpRequestWithExpectedNewParams() {
        // given
        givenHttpClientResponse(200);

        // when
        httpApplicationSettings.getStoredData(new HashSet<>(asList("id1", "id2")), new HashSet<>(asList("id3", "id4")),
                timeout);

        // then
        verify(httpClient).get(eq("http://stored-requests?request-ids=id2,id1&imp-ids=id4,id3"), any(), anyLong());
    }

    @Test
    public void getStoredDataShouldSendHttpRequestWithExpectedAppendedParams() {
        // given
        givenHttpClientResponse(200);
        httpApplicationSettings = new HttpApplicationSettings(httpClient, "http://some-domain?param1=value1",
                AMP_ENDPOINT);

        // when
        httpApplicationSettings.getStoredData(singleton("id1"), singleton("id2"), timeout);

        // then
        verify(httpClient).get(eq("http://some-domain?param1=value1&request-ids=id1&imp-ids=id2"), any(), anyLong());
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfHttpClientFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Request exception"));

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(singleton("id1"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getStoredIdToImp()).isEmpty();
        assertThat(future.result().getErrors())
                .containsOnly("Error fetching stored requests for ids [id1] via HTTP: Request exception");
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfHttpClientRespondsNot200Status() {
        // given
        givenHttpClientReturnsResponses(500, "ignored");

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(singleton("id1"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getStoredIdToImp()).isEmpty();
        assertThat(future.result().getErrors())
                .containsOnly("Error fetching stored requests for ids [id1] via HTTP: response code was 500");
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfHttpResponseIsMalformed() {
        // given
        givenHttpClientReturnsResponses(200, "invalid-response");

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(singleton("id1"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getStoredIdToImp()).isEmpty();
        assertThat(future.result().getErrors().get(0))
                .startsWith("Error fetching stored requests for ids [id1] via HTTP: parsing json failed for response: "
                        + "invalid-response with message: Failed to decode");
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfStoredRequestObjectIsMalformed() {
        // given
        final String malformedStoredRequest = "{\"requests\": {\"id1\":\"invalid-stored-request\"}";
        givenHttpClientReturnsResponses(200, malformedStoredRequest);

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(singleton("id1"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getStoredIdToImp()).isEmpty();
        assertThat(future.result().getErrors().get(0))
                .startsWith("Error fetching stored requests for ids [id1] via HTTP: "
                        + "parsing json failed for response: {\"requests\": {\"id1\":\"invalid-stored-request\"} "
                        + "with message: Failed to decode");
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfStoredImpObjectIsMalformed() {
        // given
        final String malformedStoredRequest = "{\"imps\": {\"id1\":\"invalid-stored-imp\"}";
        givenHttpClientReturnsResponses(200, malformedStoredRequest);

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(singleton("id1"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getStoredIdToImp()).isEmpty();
        assertThat(future.result().getErrors().get(0))
                .startsWith("Error fetching stored requests for ids [id1] via HTTP: parsing json failed for response: "
                        + "{\"imps\": {\"id1\":\"invalid-stored-imp\"} with message: Failed to decode");
    }

    @Test
    public void getStoredDataShouldTolerateMissedId() throws JsonProcessingException {
        // given
        final HttpFetcherResponse response = HttpFetcherResponse.of(
                singletonMap("id1", mapper.createObjectNode()), singletonMap("id3", mapper.createObjectNode()));
        givenHttpClientReturnsResponses(200, mapper.writeValueAsString(response));

        // when
        final Future<StoredDataResult> future = httpApplicationSettings.getStoredData(
                new HashSet<>(asList("id1", "id2")), new HashSet<>(asList("id3", "id4")), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest().entrySet()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("id1", "{}"));
        assertThat(future.result().getStoredIdToImp().entrySet()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("id3", "{}"));
        assertThat(future.result().getErrors()).hasSize(2)
                .containsOnly("Stored request not found for id: id2", "Stored imp not found for id: id4");
    }

    @Test
    public void getStoredDataShouldReturnExpectedResult() throws JsonProcessingException {
        // given
        final HttpFetcherResponse response = HttpFetcherResponse.of(
                singletonMap("id1", mapper.createObjectNode().put("field1", "field-value1")),
                singletonMap("id2", mapper.createObjectNode().put("field2", "field-value2")));
        givenHttpClientReturnsResponses(200, mapper.writeValueAsString(response));

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(singleton("id1"), singleton("id2"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest().entrySet()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("id1", "{\"field1\":\"field-value1\"}"));
        assertThat(future.result().getStoredIdToImp().entrySet()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("id2", "{\"field2\":\"field-value2\"}"));
    }

    @Test
    public void getAmpStoredDataShouldIgnoreImpIdsArgument() {
        // given
        givenHttpClientResponse(200);

        // when
        httpApplicationSettings.getAmpStoredData(singleton("id1"), singleton("id2"), timeout);

        // then
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(captor.capture(), any(), anyLong());
        assertThat(captor.getValue()).doesNotContain("imp-ids");
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
        given(httpClientResponse.statusCode()).willReturn(statusCode);

        given(httpClient.get(anyString(), any(), anyLong()))
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
