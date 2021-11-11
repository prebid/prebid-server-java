package org.prebid.server.analytics.pubstack;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.pubstack.model.EventType;
import org.prebid.server.analytics.pubstack.model.PubstackAnalyticsProperties;
import org.prebid.server.analytics.pubstack.model.PubstackConfig;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class PubstackAnalyticsReporterTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;

    @Mock
    private HttpClient httpClient;

    @Mock
    private PubstackEventHandler auctionHandler;

    @Mock
    private PubstackEventHandler setuidHandler;

    private PubstackAnalyticsReporter pubstackAnalyticsReporter;

    private PubstackAnalyticsProperties properties;

    @Before
    public void setUp() {
        given(vertx.setPeriodic(anyLong(), any())).willReturn(1L, 2L);
        properties = PubstackAnalyticsProperties.builder()
                .endpoint("http://endpoint.com")
                .scopeId("scopeId")
                .sizeBytes(100000)
                .count(100)
                .reportTtlMs(10000L)
                .timeoutMs(5000L)
                .configurationRefreshDelayMs(200000L)
                .build();

        final Map<EventType, PubstackEventHandler> handlers = new HashMap<>();
        handlers.put(EventType.auction, auctionHandler);
        handlers.put(EventType.setuid, setuidHandler);

        pubstackAnalyticsReporter = new PubstackAnalyticsReporter(properties, httpClient, jacksonMapper,
                vertx);
        // inject mocked handlers to private fields without accessor method
        ReflectionTestUtils.setField(pubstackAnalyticsReporter, "eventHandlers", handlers);
    }

    @Test
    public void initializeShouldFetchConfigAndSetPeriodicTimerForConfigUpdate() throws JsonProcessingException {
        // given
        final PubstackConfig pubstackConfig = PubstackConfig.of("newScopeId", "http://newendpoint",
                Collections.singletonMap(EventType.auction, true));
        given(httpClient.get(anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, mapper.writeValueAsString(pubstackConfig))));

        // when
        pubstackAnalyticsReporter.initialize();

        // then
        verify(vertx).setPeriodic(anyLong(), any());
        verify(httpClient).get(anyString(), anyLong());
        verify(auctionHandler).reportEvents();
        verify(setuidHandler).reportEvents();
        verify(auctionHandler).updateConfig(eq(true), eq("http://newendpoint/intake/auction"), eq("newScopeId"));
        verify(setuidHandler).updateConfig(eq(false), eq("http://newendpoint/intake/setuid"), eq("newScopeId"));
    }

    @Test
    public void initializeShouldFailUpdateSendBuffersAndSetTimerWhenEndpointFromRemoteConfigIsNotValid()
            throws JsonProcessingException {
        // given
        final PubstackConfig pubstackConfig = PubstackConfig.of("newScopeId", "invalid",
                Collections.singletonMap(EventType.auction, true));
        given(httpClient.get(anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, mapper.writeValueAsString(pubstackConfig))));

        // when and then
        assertThatThrownBy(() -> pubstackAnalyticsReporter.initialize())
                .hasMessage("[pubstack] Failed to create event report url for endpoint: invalid")
                .isInstanceOf(PreBidException.class);
        verify(auctionHandler).reportEvents();
        verify(setuidHandler).reportEvents();
        verifyNoMoreInteractions(auctionHandler);
        verifyNoMoreInteractions(setuidHandler);
        verify(vertx).setPeriodic(anyLong(), any());
    }

    @Test
    public void initializeShouldNotUpdateEventsIfFetchedConfigIsSameAsPrevious() throws JsonProcessingException {
        // given
        final PubstackConfig pubstackConfig = PubstackConfig.of("newScopeId", "http://newendpoint",
                Collections.singletonMap(EventType.auction, true));
        given(httpClient.get(anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, mapper.writeValueAsString(pubstackConfig))));

        // when
        pubstackAnalyticsReporter.initialize();
        pubstackAnalyticsReporter.initialize();

        // then
        verify(httpClient, times(2)).get(anyString(), anyLong());
        // just once for first initialization
        verify(auctionHandler).reportEvents();
        verify(setuidHandler).reportEvents();
        verify(auctionHandler).updateConfig(eq(true), eq("http://newendpoint/intake/auction"), eq("newScopeId"));
        verify(setuidHandler).updateConfig(eq(false), eq("http://newendpoint/intake/setuid"), eq("newScopeId"));
    }

    @Test
    public void initializeShouldNotSendEventsAndUpdateConfigsWhenResponseStatusIsNot200() {
        // given
        given(httpClient.get(anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(400, null, null)));

        // when
        pubstackAnalyticsReporter.initialize();

        // then
        verify(vertx).setPeriodic(anyLong(), any());
        verify(httpClient).get(anyString(), anyLong());
        verifyNoInteractions(auctionHandler);
        verifyNoInteractions(setuidHandler);
    }

    @Test
    public void initializeShouldNotSendEventsAndUpdateConfigsWhenCantParseResponse() {
        // given
        given(httpClient.get(anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, "{\"endpoint\" : {}}")));

        // when
        pubstackAnalyticsReporter.initialize();

        // then
        verify(vertx).setPeriodic(anyLong(), any());
        verify(httpClient).get(anyString(), anyLong());
        verifyNoInteractions(auctionHandler);
        verifyNoInteractions(setuidHandler);
    }

    @Test
    public void shutdownShouldCallSendEventsOnAllEventHandlers() {
        // given and when
        pubstackAnalyticsReporter.shutdown();

        // then
        verify(auctionHandler).reportEvents();
        verify(setuidHandler).reportEvents();
    }

    @Test
    public void processEventShouldCallEventHandlerForAuction() {
        // given
        pubstackAnalyticsReporter = new PubstackAnalyticsReporter(properties, httpClient, jacksonMapper, vertx);
        // inject mocked handler to private fields without accessor method
        ReflectionTestUtils.setField(pubstackAnalyticsReporter, "eventHandlers",
                Collections.singletonMap(EventType.auction, auctionHandler));
        final AuctionEvent auctionEvent = AuctionEvent.builder().build();

        // when
        pubstackAnalyticsReporter.processEvent(auctionEvent);

        // then
        verify(auctionHandler).handle(same(auctionEvent));
    }

    @Test
    public void processEventShouldCallEventHandlerForSetuid() {
        // given
        pubstackAnalyticsReporter = new PubstackAnalyticsReporter(properties, httpClient, jacksonMapper, vertx);
        // inject mocked handler to private fields without accessor method
        ReflectionTestUtils.setField(pubstackAnalyticsReporter, "eventHandlers",
                Collections.singletonMap(EventType.setuid, setuidHandler));
        final SetuidEvent setuidEvent = SetuidEvent.builder().build();

        // when
        pubstackAnalyticsReporter.processEvent(setuidEvent);

        // then
        verify(setuidHandler).handle(same(setuidEvent));
    }

    @Test
    public void processEventShouldCallEventHandlerForCookieSync() {
        // given
        final PubstackEventHandler cookieSyncHandler = mock(PubstackEventHandler.class);
        pubstackAnalyticsReporter = new PubstackAnalyticsReporter(properties, httpClient, jacksonMapper, vertx);
        // inject mocked handler to private fields without accessor method
        ReflectionTestUtils.setField(pubstackAnalyticsReporter, "eventHandlers",
                Collections.singletonMap(EventType.cookiesync, cookieSyncHandler));
        final CookieSyncEvent cookieSyncEvent = CookieSyncEvent.builder().build();

        // when
        pubstackAnalyticsReporter.processEvent(cookieSyncEvent);

        // then
        verify(cookieSyncHandler).handle(same(cookieSyncEvent));
    }

    @Test
    public void processEventShouldCallEventHandlerForAmp() {
        // given
        final PubstackEventHandler ampHandler = mock(PubstackEventHandler.class);
        pubstackAnalyticsReporter = new PubstackAnalyticsReporter(properties, httpClient, jacksonMapper, vertx);
        // inject mocked handler to private fields without accessor method
        ReflectionTestUtils.setField(pubstackAnalyticsReporter, "eventHandlers",
                Collections.singletonMap(EventType.amp, ampHandler));
        final AmpEvent ampEvent = AmpEvent.builder().build();

        // when
        pubstackAnalyticsReporter.processEvent(ampEvent);

        // then
        verify(ampHandler).handle(same(ampEvent));
    }

    @Test
    public void processEventShouldCallEventHandlerForVideo() {
        // given
        final PubstackEventHandler videoHandler = mock(PubstackEventHandler.class);
        pubstackAnalyticsReporter = new PubstackAnalyticsReporter(properties, httpClient, jacksonMapper, vertx);
        // inject mocked handler to private fields without accessor method
        ReflectionTestUtils.setField(pubstackAnalyticsReporter, "eventHandlers",
                Collections.singletonMap(EventType.video, videoHandler));

        final VideoEvent videoEvent = VideoEvent.builder().build();

        // when
        pubstackAnalyticsReporter.processEvent(videoEvent);

        // then
        verify(videoHandler).handle(same(videoEvent));
    }
}
