package org.prebid.server.analytics.reporter.liveintent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.reporter.liveintent.model.LiveIntentAnalyticsProperties;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LiveintentAnalyticsReporterTest extends VertxTest {

    private static final HttpClientResponse SUCCESS_RESPONSE = HttpClientResponse.of(
            200, MultiMap.caseInsensitiveMultiMap(), "OK");

    @Mock
    private HttpClient httpClient;

    private LiveIntentAnalyticsReporter target;

    private LiveIntentAnalyticsProperties properties;

    private JacksonMapper jacksonMapper;

    @BeforeEach
    public void setUp() {
        final ObjectMapper mapper = new ObjectMapper();
        jacksonMapper = new JacksonMapper(mapper);

        properties = LiveIntentAnalyticsProperties.builder()
                .analyticsEndpoint("https://localhost:8080")
                .partnerId("pbsj")
                .timeoutMs(1000L)
                .build();

        target = new LiveIntentAnalyticsReporter(
                properties,
                httpClient,
                jacksonMapper);
    }

    @Test
    public void shouldProcessNotificationEvent() {

        // when
        target.processEvent(NotificationEvent.builder().bidId("123").bidder("foo").build());
        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);

        when(httpClient.get(anyString(), anyLong())).thenReturn(Future.succeededFuture(mockResponse));
        // then
        // Verify that the HTTP client was called with the expected parameters
        verify(httpClient).get(eq(properties.getAnalyticsEndpoint() + "/analytic-events/pbsj-winning-bid")
                + "?b=foo&bidId=123", eq(properties.getTimeoutMs()));
    }
}
