package org.prebid.server.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.proto.response.HttpRefreshResponse;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class HttpPeriodicRefreshServiceTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://stored-requests.prebid.com";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CacheNotificationListener cacheNotificationListener;
    @Mock
    private HttpClient httpClient;
    @Mock
    private Vertx vertx;

    private HttpClientResponse updatedResponse;
    private final Map<String, String> expectedRequests = singletonMap("id1", "{\"field1\":\"field-value1\"}");
    private final Map<String, String> expectedImps = singletonMap("id2", "{\"field2\":\"field-value2\"}");

    @Before
    public void setUp() throws JsonProcessingException {

        final HttpClientResponse initialResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(HttpRefreshResponse.of(
                        singletonMap("id1", mapper.createObjectNode().put("field1", "field-value1")),
                        singletonMap("id2", mapper.createObjectNode().put("field2", "field-value2")))));
        updatedResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(HttpRefreshResponse.of(
                        singletonMap("id1", mapper.createObjectNode().put("deleted", "true")),
                        singletonMap("id2", mapper.createObjectNode().put("field2", "field-value2")))));

        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(initialResponse));
        given(httpClient.get(contains("?last-modified="), anyLong()))
                .willReturn(Future.succeededFuture(updatedResponse));
    }

    @Test
    public void creationShouldFailOnInvalidUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> createAndInitService(cacheNotificationListener,
                "invalid_url", 1, 1, vertx, httpClient));
    }

    @Test
    public void shouldCallSaveWithExpectedParameters() {
        // when
        createAndInitService(cacheNotificationListener, ENDPOINT_URL,
                1000, 2000, vertx, httpClient);

        // then
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
    }

    @Test
    public void shouldCallInvalidateAndSaveWithExpectedParameters() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));

        // when
        createAndInitService(cacheNotificationListener, ENDPOINT_URL,
                1000, 2000, vertx, httpClient);

        // then
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
        verify(cacheNotificationListener).invalidate(singletonList("id1"), emptyList());
        verify(cacheNotificationListener).save(emptyMap(), expectedImps);
    }

    @Test
    public void shouldCallSaveAfterUpdate() throws JsonProcessingException {
        // given
        updatedResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(HttpRefreshResponse.of(
                        singletonMap("id1", mapper.createObjectNode().put("changed1", "value-changed2")),
                        singletonMap("id2", mapper.createObjectNode().put("field2", "field-value2")))));

        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));
        given(httpClient.get(contains("?last-modified="), anyLong()))
                .willReturn(Future.succeededFuture(updatedResponse));

        // when
        createAndInitService(cacheNotificationListener, ENDPOINT_URL,
                1000, 2000, vertx, httpClient);

        // then
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
        verify(cacheNotificationListener).save(singletonMap("id1", "{\"changed1\":\"value-changed2\"}"), expectedImps);
    }

    @Test
    public void initializeShouldMakeOneInitialRequestAndTwoScheduledRequestsWithParam() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L, 2L));

        // when
        createAndInitService(cacheNotificationListener, ENDPOINT_URL,
                1000, 2000, vertx, httpClient);

        // then
        verify(httpClient).get(eq("http://stored-requests.prebid.com"), anyLong());
        verify(httpClient, times(2))
                .get(startsWith("http://stored-requests.prebid.com?last-modified="), anyLong());
    }

    @Test
    public void initializeShouldMakeOnlyOneInitialRequestIfRefreshPeriodIsNegative() {
        // when
        createAndInitService(cacheNotificationListener, ENDPOINT_URL,
                -1, 2000, vertx, httpClient);

        // then
        verify(vertx, never()).setPeriodic(anyLong(), any());
        verify(httpClient).get(anyString(), anyLong());
    }

    @Test
    public void shouldModifyEndpointUrlCorrectlyIfUrlHasParameters() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));

        // when
        createAndInitService(cacheNotificationListener, ENDPOINT_URL + "?amp=true",
                1000, 2000, vertx, httpClient);

        // then
        verify(httpClient).get(startsWith("http://stored-requests.prebid.com?amp=true&last-modified="), anyLong());
    }

    private static void createAndInitService(CacheNotificationListener notificationListener,
                                             String url, long refreshPeriod, long timeout,
                                             Vertx vertx, HttpClient httpClient) {
        final HttpPeriodicRefreshService httpPeriodicRefreshService = new HttpPeriodicRefreshService(
                url, refreshPeriod, timeout, notificationListener, vertx, httpClient, jacksonMapper);
        httpPeriodicRefreshService.initialize();
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T... objects) {
        return inv -> {
            // invoking handler right away passing mock to it
            for (T obj : objects) {
                ((Handler<T>) inv.getArgument(1)).handle(obj);
            }
            return 0L;
        };
    }
}
