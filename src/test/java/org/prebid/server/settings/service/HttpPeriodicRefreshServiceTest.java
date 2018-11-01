package org.prebid.server.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.proto.response.HttpRefreshResponse;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


public class HttpPeriodicRefreshServiceTest extends VertxTest {


    private static final String ENDPOINT_URL = "http://stored-requests.prebid.com";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CacheNotificationListener cacheNotificationListener;

    @Mock
    private HttpClient httpClient;

    private Vertx vertx;

    private HttpPeriodicRefreshService httpPeriodicRefreshService;

    private HttpRefreshResponse httpRefreshResponse;
    private HttpClientResponse updateResponse;
    private Map<String, String> expectedRequests;
    private Map<String, String> expectedImps;

    @Before
    public void setUp() throws JsonProcessingException {
        vertx = Vertx.vertx();

        httpRefreshResponse = HttpRefreshResponse.of(
                singletonMap("id1", mapper.createObjectNode().put("field1", "field-value1")),
                singletonMap("id2", mapper.createObjectNode().put("field2", "field-value2")));

        givenHttpClientReturnsResponse(httpClient, mapper.writeValueAsString(httpRefreshResponse));

        updateResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(HttpRefreshResponse.of(
                        singletonMap("id1", mapper.createObjectNode().put("deleted", "true")),
                        singletonMap("id2", mapper.createObjectNode().put("field2", "field-value2")))));

        given(httpClient.get(contains(ENDPOINT_URL + "?last-modified="), anyLong()))
                .willReturn(Future.succeededFuture(updateResponse));

        expectedRequests = singletonMap("id1", "{\"field1\":\"field-value1\"}");
        expectedImps = singletonMap("id2", "{\"field2\":\"field-value2\"}");

        httpPeriodicRefreshService = createAndInitService(cacheNotificationListener, ENDPOINT_URL,
                1000, 2000, vertx, httpClient);
    }

    @After
    public void cleanUp() {
        vertx.close();
    }

    @Test
    public void creationShouldFailOnInvalidUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> createAndInitService(cacheNotificationListener,
                "invalid_url", 1, 1, vertx, httpClient));
    }

    @Test
    public void shouldCallSaveWithExpectedParameters() {
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
    }

    @Test
    public void shouldCallInvalidateAndSaveWithExpectedParameters() {
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
        verify(cacheNotificationListener, after(1100)).invalidate(singletonList("id1"), emptyList());
        verify(cacheNotificationListener).save(emptyMap(), expectedImps);

    }

    @Test
    public void shouldCallSaveAfterUpdate() throws JsonProcessingException {
        // given
        updateResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(HttpRefreshResponse.of(
                        singletonMap("id1", mapper.createObjectNode().put("changed1", "value-changed2")),
                        singletonMap("id2", mapper.createObjectNode().put("field2", "field-value2")))));

        given(httpClient.get(contains(ENDPOINT_URL + "?last-modified="), anyLong()))
                .willReturn(Future.succeededFuture(updateResponse));

        // when and then
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
        verify(cacheNotificationListener, after(1100)).invalidate(emptyList(), emptyList());
        verify(cacheNotificationListener).save(singletonMap("id1","{\"changed1\":\"value-changed2\"}"), expectedImps);
    }

    @Test
    public void initializeShouldMakeOneInitialRequestAndTwoScheduledRequestsWithParam() {
        verify(httpClient).get(eq("http://stored-requests.prebid.com"), anyLong());
        verify(httpClient, after(2100).times(2)).get(contains("http://stored-requests.prebid.com?last-modified="), anyLong());
    }

    @Test
    public void initializeShouldMakeOnlyOneInitialRequestIfRefreshPeriodIsNegative() throws JsonProcessingException {
        // given
        final Vertx vertx = Vertx.vertx();
        final HttpClient httpClient = mock(HttpClient.class);
        givenHttpClientReturnsResponse(httpClient, mapper.writeValueAsString(httpRefreshResponse));

        // when
        httpPeriodicRefreshService = createAndInitService(cacheNotificationListener, ENDPOINT_URL,
                -1, 2000, vertx, httpClient);

        // then
        verify(httpClient, after(1100).times(1)).get(anyString(), anyLong());
    }

    private static HttpPeriodicRefreshService createAndInitService(CacheNotificationListener notificationListener,
                                                                   String url, long refreshPeriod, long timeout,
                                                                   Vertx vertx, HttpClient httpClient) {
        final HttpPeriodicRefreshService httpPeriodicRefreshService =
                new HttpPeriodicRefreshService(notificationListener, url, refreshPeriod, timeout, vertx, httpClient);
        httpPeriodicRefreshService.initialize();
        return httpPeriodicRefreshService;
    }

    private static void givenHttpClientReturnsResponse(HttpClient httpClient, String response) {
        final HttpClientResponse httpClientResponse = HttpClientResponse.of(200, null, response);
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));
    }
}