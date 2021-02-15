package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.settings.proto.response.HttpAccountsResponse;
import org.prebid.server.settings.proto.response.HttpFetcherResponse;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class HttpApplicationSettingsTest extends VertxTest {

    private static final String ENDPOINT = "http://stored-requests";
    private static final String AMP_ENDPOINT = "http://amp-stored-requests";
    private static final String VIDEO_ENDPOINT = "http://video-stored-requests";
    private static final String CATEGORY_ENDPOINT = "http://category-requests";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private HttpApplicationSettings httpApplicationSettings;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
        httpApplicationSettings = new HttpApplicationSettings(httpClient, jacksonMapper, ENDPOINT, AMP_ENDPOINT,
                VIDEO_ENDPOINT, CATEGORY_ENDPOINT);

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);
    }

    @Test
    public void creationShouldFailsOnInvalidEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpApplicationSettings(httpClient, jacksonMapper, "invalid_url", AMP_ENDPOINT,
                        VIDEO_ENDPOINT, CATEGORY_ENDPOINT))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldFailsOnInvalidAmpEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpApplicationSettings(httpClient, jacksonMapper, ENDPOINT, "invalid_url",
                        VIDEO_ENDPOINT, CATEGORY_ENDPOINT))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldFailsOnInvalidVideoEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpApplicationSettings(httpClient, jacksonMapper, ENDPOINT, AMP_ENDPOINT,
                        "invalid_url", CATEGORY_ENDPOINT))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void getAccountByIdShouldReturnFetchedAccount() throws JsonProcessingException {
        // given
        final Account account = Account.builder()
                .id("someId")
                .enforceCcpa(true)
                .priceGranularity("testPriceGranularity")
                .build();
        HttpAccountsResponse response = HttpAccountsResponse.of(Collections.singletonMap("someId", account));
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(response));

        // when
        final Future<Account> future = httpApplicationSettings.getAccountById("someId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getId()).isEqualTo("someId");
        assertThat(future.result().getEnforceCcpa()).isEqualTo(true);
        assertThat(future.result().getPriceGranularity()).isEqualTo("testPriceGranularity");

        verify(httpClient).get(eq("http://stored-requests?account-ids=[\"someId\"]"), any(),
                anyLong());
    }

    @Test
    public void getAccountByIdShouldReturnFaildedFutureIfResponseIsNotPresent() throws JsonProcessingException {
        // given
        HttpAccountsResponse response = HttpAccountsResponse.of(null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(response));

        // when
        final Future<Account> future = httpApplicationSettings.getAccountById("notFoundId", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Account with id : notFoundId not found");
    }

    @Test
    public void getAccountByIdShouldReturnErrorIdAccountNotFound() throws JsonProcessingException {
        // given
        HttpAccountsResponse response = HttpAccountsResponse.of(Collections.emptyMap());
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(response));

        // when
        final Future<Account> future = httpApplicationSettings.getAccountById("notExistingId", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Account with id : notExistingId not found");
    }

    @Test
    public void getAccountByIdShouldReturnErrorIfResponseStatusIsDifferentFromOk() {
        // given
        givenHttpClientReturnsResponse(400, null);

        // when
        final Future<Account> future = httpApplicationSettings.getAccountById("accountId", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Error fetching accounts [accountId] via http: unexpected response status 400");
    }

    @Test
    public void getAccountByIdShouldReturnErrorIfResponseHasInvalidStructure() {
        // given
        givenHttpClientReturnsResponse(200, "not valid response");

        // when
        final Future<Account> future = httpApplicationSettings.getAccountById("accountId", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessageContaining("Error fetching accounts [accountId] via http: "
                        + "failed to parse response: Failed to decode:");
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnEmptyResult() {
        // when
        final Future<String> future = httpApplicationSettings.getAdUnitConfigById(null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class).hasMessage("Not supported");
    }

    @Test
    public void getStoredResponsesShouldReturnFailedFutureWithNotSupportedReason() {
        // when
        final Future<StoredResponseDataResult> future = httpApplicationSettings.getStoredResponses(null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class).hasMessage("Not supported");
    }

    @Test
    public void getStoredDataShouldReturnEmptyResultIfEmptyRequestsIdsGiven() {
        // when
        final Future<StoredDataResult> future = httpApplicationSettings.getStoredData(null, emptySet(),
                emptySet(), null);

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
                httpApplicationSettings.getStoredData(null, singleton("id1"), emptySet(), expiredTimeout);

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
        givenHttpClientReturnsResponse(200, null);

        // when
        httpApplicationSettings.getStoredData(null, new HashSet<>(asList("id1", "id2")),
                new HashSet<>(asList("id3", "id4")), timeout);

        // then
        verify(httpClient).get(eq("http://stored-requests?request-ids=[\"id2\",\"id1\"]&imp-ids=[\"id4\",\"id3\"]"),
                any(), anyLong());
    }

    @Test
    public void getStoredDataShouldSendHttpRequestWithExpectedAppendedParams() {
        // given
        givenHttpClientReturnsResponse(200, null);
        httpApplicationSettings = new HttpApplicationSettings(httpClient, jacksonMapper,
                "http://some-domain?param1=value1", AMP_ENDPOINT, VIDEO_ENDPOINT, CATEGORY_ENDPOINT);

        // when
        httpApplicationSettings.getStoredData(null, singleton("id1"), singleton("id2"), timeout);

        // then
        verify(httpClient).get(eq("http://some-domain?param1=value1&request-ids=[\"id1\"]&imp-ids=[\"id2\"]"), any(),
                anyLong());
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfHttpClientFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Request exception"));

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(null, singleton("id1"), emptySet(), timeout);

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
        givenHttpClientReturnsResponse(500, "ignored");

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(null, singleton("id1"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getStoredIdToImp()).isEmpty();
        assertThat(future.result().getErrors())
                .containsOnly("Error fetching stored requests for ids [id1] via HTTP: HTTP status code 500");
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfHttpResponseIsMalformed() {
        // given
        givenHttpClientReturnsResponse(200, "invalid-response");

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(null, singleton("id1"), emptySet(), timeout);

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
        givenHttpClientReturnsResponse(200, malformedStoredRequest);

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(null, singleton("id1"), emptySet(), timeout);

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
        givenHttpClientReturnsResponse(200, malformedStoredRequest);

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(null, singleton("id1"), emptySet(), timeout);

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
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(response));

        // when
        final Future<StoredDataResult> future = httpApplicationSettings.getStoredData(
                null, new HashSet<>(asList("id1", "id2")), new HashSet<>(asList("id3", "id4")), timeout);

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
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(response));

        // when
        final Future<StoredDataResult> future =
                httpApplicationSettings.getStoredData(null, singleton("id1"), singleton("id2"), timeout);

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
        givenHttpClientReturnsResponse(200, null);

        // when
        httpApplicationSettings.getAmpStoredData(null, singleton("id1"), singleton("id2"), timeout);

        // then
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(captor.capture(), any(), anyLong());
        assertThat(captor.getValue()).doesNotContain("imp-ids");
    }

    @Test
    public void getCategoriesShouldBuildUrlFromEndpointAdServerAndPublisher() {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "{}")));

        // when
        httpApplicationSettings.getCategories("primaryAdServer", "publisher", timeout);

        // then
        final ArgumentCaptor<String> urlArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(urlArgumentCaptor.capture(), anyLong());
        assertThat(urlArgumentCaptor.getValue()).isEqualTo("http://category-requests/primaryAdServer/publisher.json");
    }

    @Test
    public void getCategoriesShouldBuildUrlFromEndpointAdServer() {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "{}")));

        // when
        httpApplicationSettings.getCategories("primaryAdServer", null, timeout);

        // then
        final ArgumentCaptor<String> urlArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(urlArgumentCaptor.capture(), anyLong());
        assertThat(urlArgumentCaptor.getValue()).isEqualTo("http://category-requests/primaryAdServer.json");
    }

    @Test
    public void getCategoriesShouldReturnFailedFutureWithTimeoutException() {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "{}")));

        // when
        final Future<Map<String, String>> result
                = httpApplicationSettings.getCategories("primaryAdServer", null, expiredTimeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(TimeoutException.class)
                .hasMessage("Failed to fetch categories from url 'http://category-requests/primaryAdServer.json'."
                        + " Reason: Timeout exceeded");
    }

    @Test
    public void getCategoriesShouldReturnFailedFutureWhenResponseStatusIsNot200() {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, null, "{}")));

        // when
        final Future<Map<String, String>> result
                = httpApplicationSettings.getCategories("primaryAdServer", null, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Failed to fetch categories from url 'http://category-requests/primaryAdServer.json'."
                        + " Reason: Response status code is '400'");
    }

    @Test
    public void getCategoriesShouldReturnFailedFutureWhenBodyIsNull() {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, null)));

        // when
        final Future<Map<String, String>> result
                = httpApplicationSettings.getCategories("primaryAdServer", null, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Failed to fetch categories from url 'http://category-requests/primaryAdServer.json'."
                        + " Reason: Response body is null or empty");
    }

    @Test
    public void getCategoriesShouldReturnFailedFutureWhenBodyCantBeParsed() {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "{\"iab\": {\"id\": {}}}")));

        // when
        final Future<Map<String, String>> result
                = httpApplicationSettings.getCategories("primaryAdServer", null, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Failed to fetch categories from url "
                        + "'http://category-requests/primaryAdServer.json'. Reason: Failed to decode response body");
    }

    @Test
    public void getCategoriesShouldReturnResult() {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "{\"iab\": {\"id\": \"id\"}}")));

        // when
        final Future<Map<String, String>> result
                = httpApplicationSettings.getCategories("primaryAdServer", null, timeout);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).hasSize(1)
                .containsEntry("iab", "id");
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        given(httpClient.get(anyString(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(statusCode, null, response)));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        given(httpClient.get(anyString(), any(), anyLong()))
                .willReturn(Future.failedFuture(throwable));
    }
}
