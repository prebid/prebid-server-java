package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.settings.proto.response.HttpAccountsResponse;
import org.prebid.server.settings.proto.response.HttpFetcherResponse;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class HttpApplicationSettingsTest extends VertxTest {

    private static final String ENDPOINT = "http://stored-requests.com/something?id=1";
    private static final String AMP_ENDPOINT = "http://amp-stored-requests.com/something?id=2";
    private static final String VIDEO_ENDPOINT = "http://video-stored-requests.com/something?id=3";
    private static final String CATEGORY_ENDPOINT = "http://category-requests.com/something";

    @Mock(strictness = LENIENT)
    private HttpClient httpClient;

    private HttpApplicationSettings httpApplicationSettings;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @BeforeEach
    public void setUp() {
        httpApplicationSettings = new HttpApplicationSettings(httpClient, jacksonMapper, ENDPOINT, AMP_ENDPOINT,
                VIDEO_ENDPOINT, CATEGORY_ENDPOINT, false);

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);
    }

    @Test
    public void creationShouldFailsOnInvalidEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpApplicationSettings(httpClient, jacksonMapper, "invalid_url", AMP_ENDPOINT,
                        VIDEO_ENDPOINT, CATEGORY_ENDPOINT, false))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldFailsOnInvalidAmpEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpApplicationSettings(httpClient, jacksonMapper, ENDPOINT, "invalid_url",
                        VIDEO_ENDPOINT, CATEGORY_ENDPOINT, false))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldFailsOnInvalidVideoEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpApplicationSettings(httpClient, jacksonMapper, ENDPOINT, AMP_ENDPOINT,
                        "invalid_url", CATEGORY_ENDPOINT, false))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void getAccountByIdShouldReturnFetchedAccount() throws JsonProcessingException {
        // given
        final Account account = Account.builder()
                .id("someId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("testPriceGranularity")
                        .build())
                .privacy(AccountPrivacyConfig.builder().build())
                .build();
        final HttpAccountsResponse response = HttpAccountsResponse.of(Collections.singletonMap("someId", account));
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(response));

        // when
        final Future<Account> future = httpApplicationSettings.getAccountById("someId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getId()).isEqualTo("someId");
        assertThat(future.result().getAuction().getPriceGranularity()).isEqualTo("testPriceGranularity");

        verify(httpClient).get(
                eq("http://stored-requests.com/something?id=1&account-ids=%5B%22someId%22%5D"),
                any(),
                anyLong());
    }

    @Test
    public void getAccountByIdShouldReturnFetchedAccountWithRfc3986CompatibleParams() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, null);
        httpApplicationSettings = new HttpApplicationSettings(httpClient, jacksonMapper,
                ENDPOINT, AMP_ENDPOINT, VIDEO_ENDPOINT, CATEGORY_ENDPOINT, true);

        final Account account = Account.builder()
                .id("someId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("testPriceGranularity")
                        .build())
                .privacy(AccountPrivacyConfig.builder().build())
                .build();
        final HttpAccountsResponse response = HttpAccountsResponse.of(Collections.singletonMap("someId", account));
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(response));

        // when
        final Future<Account> future = httpApplicationSettings.getAccountById("someId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getId()).isEqualTo("someId");
        assertThat(future.result().getAuction().getPriceGranularity()).isEqualTo("testPriceGranularity");

        verify(httpClient).get(
                eq("http://stored-requests.com/something?id=1&account-id=someId"),
                any(),
                anyLong());
    }

    @Test
    public void getAccountByIdShouldReturnFaildedFutureIfResponseIsNotPresent() throws JsonProcessingException {
        // given
        final HttpAccountsResponse response = HttpAccountsResponse.of(null);
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
        final HttpAccountsResponse response = HttpAccountsResponse.of(Collections.emptyMap());
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
        verifyNoInteractions(httpClient);
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
        verifyNoInteractions(httpClient);
    }

    @Test
    public void getStoredDataShouldSendHttpRequestWithExpectedNewParams() {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        httpApplicationSettings.getStoredData(null, new HashSet<>(asList("id1", "id2")),
                new HashSet<>(asList("id3", "id4")), timeout);

        // then
        verify(httpClient).get(
                eq("http://stored-requests.com/something"
                        + "?id=1&request-ids=%5B%22id2%22%2C%22id1%22%5D&imp-ids=%5B%22id4%22%2C%22id3%22%5D"),
                any(),
                anyLong());
    }

    @Test
    public void getStoredDataShouldSendHttpRequestWithExpectedAppendedParams() {
        // given
        givenHttpClientReturnsResponse(200, null);
        httpApplicationSettings = new HttpApplicationSettings(httpClient, jacksonMapper,
                "http://some-domain.com?param1=value1", AMP_ENDPOINT, VIDEO_ENDPOINT, CATEGORY_ENDPOINT, false);

        // when
        httpApplicationSettings.getStoredData(null, singleton("id1"), singleton("id2"), timeout);

        // then
        verify(httpClient).get(
                eq("http://some-domain.com?param1=value1&request-ids=%5B%22id1%22%5D&imp-ids=%5B%22id2%22%5D"),
                any(),
                anyLong());
    }

    @Test
    public void getStoredDataShouldSendHttpRequestWithRfc3986CompatibleParams() throws URISyntaxException {
        // given
        givenHttpClientReturnsResponse(200, null);
        httpApplicationSettings = new HttpApplicationSettings(httpClient, jacksonMapper,
                ENDPOINT, AMP_ENDPOINT, VIDEO_ENDPOINT, CATEGORY_ENDPOINT, true);

        // when
        httpApplicationSettings.getStoredData(null, Set.of("id1", "id2"), Set.of("id1", "id2"), timeout);

        // then
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(captor.capture(), any(), anyLong());

        final URIBuilder uriBuilder = new URIBuilder(captor.getValue());
        assertThat(uriBuilder.getHost()).isEqualTo("stored-requests.com");
        assertThat(uriBuilder.getPath()).isEqualTo("/something");
        assertThat(uriBuilder.getQueryParams())
                .extracting(NameValuePair::getName, NameValuePair::getValue)
                .containsExactlyInAnyOrder(
                        tuple("id", "1"),
                        tuple("request-id", "id1"),
                        tuple("request-id", "id2"),
                        tuple("imp-id", "id1"),
                        tuple("imp-id", "id2"));
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
        assertThat(future.result().getErrors().getFirst())
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
        assertThat(future.result().getErrors().getFirst())
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
        assertThat(future.result().getErrors().getFirst())
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
        assertThat(urlArgumentCaptor.getValue()).isEqualTo("http://category-requests.com/something/primaryAdServer/publisher.json");
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
        assertThat(urlArgumentCaptor.getValue())
                .isEqualTo("http://category-requests.com/something/primaryAdServer.json");
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
                .hasMessage("Failed to fetch categories from url 'http://category-requests.com/something/primaryAdServer.json'."
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
                .hasMessage("Failed to fetch categories from url 'http://category-requests.com/something/primaryAdServer.json'."
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
                .hasMessage("Failed to fetch categories from url 'http://category-requests.com/something/primaryAdServer.json'."
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
                        + "'http://category-requests.com/something/primaryAdServer.json'. Reason: Failed to decode response body");
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
