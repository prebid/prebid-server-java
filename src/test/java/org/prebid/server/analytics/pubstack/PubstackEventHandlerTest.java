package org.prebid.server.analytics.pubstack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.pubstack.model.PubstackAnalyticsProperties;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.deals.model.DeepDebugLog;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.execution.Timeout;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class PubstackEventHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;

    @Mock
    private HttpClient httpClient;

    private PubstackEventHandler pubstackEventHandler;

    @Before
    public void setUp() {
        given(vertx.setTimer(anyLong(), any())).willReturn(1L, 2L);
        final PubstackAnalyticsProperties properties = PubstackAnalyticsProperties.builder()
                .endpoint("http://endpoint.com")
                .scopeId("scopeId")
                .sizeBytes(100000)
                .count(100)
                .reportTtlMs(10000L)
                .timeoutMs(5000L)
                .build();
        pubstackEventHandler = new PubstackEventHandler(properties, true, "http://example.com", jacksonMapper,
                httpClient, vertx);
    }

    @Test
    public void handleShouldNotAcceptEventsWhenNotEnabled() {
        // given
        final PubstackAnalyticsProperties properties = PubstackAnalyticsProperties.builder()
                .endpoint("http://endpoint.com")
                .scopeId("scopeId")
                .sizeBytes(1)
                .count(1)
                .reportTtlMs(10000L)
                .timeoutMs(5000L)
                .build();
        pubstackEventHandler = new PubstackEventHandler(properties, false, "http://example.com", jacksonMapper,
                httpClient, vertx);

        // when
        pubstackEventHandler.handle(SetuidEvent.builder().bidder("bidder1").build());

        // then
        @SuppressWarnings("unchecked") final AtomicReference<Set<String>> events =
                (AtomicReference<Set<String>>) ReflectionTestUtils
                        .getField(pubstackEventHandler, "events");
        assertThat(events.get()).isEmpty();
        verifyNoInteractions(httpClient);
    }

    @Test
    public void handleShouldAddEventWithScopeIdAndIncreaseByteSize() throws JsonProcessingException {
        // given and when
        final SetuidEvent setuidEvent = SetuidEvent.builder().bidder("bidder1").build();
        pubstackEventHandler.handle(setuidEvent);

        // then
        final AtomicLong byteSize = (AtomicLong) ReflectionTestUtils.getField(pubstackEventHandler, "byteSize");
        @SuppressWarnings("unchecked") final AtomicReference<Set<String>> events =
                (AtomicReference<Set<String>>) ReflectionTestUtils
                        .getField(pubstackEventHandler, "events");
        final ObjectNode eventJsonNode = mapper.valueToTree(setuidEvent);
        eventJsonNode.put("scope", "scopeId");
        final String eventJsonRow = mapper.writeValueAsString(eventJsonNode);
        assertThat(byteSize.get()).isEqualTo(eventJsonRow.getBytes().length);
        assertThat(events.get()).hasSize(1)
                .containsOnly(eventJsonRow);
    }

    @Test
    public void handleShouldSendEventsWhenMaxByteBufferSizeExceedsSize() {
        // given
        final PubstackAnalyticsProperties properties = PubstackAnalyticsProperties.builder()
                .endpoint("http://endpoint.com")
                .scopeId("scopeId")
                .sizeBytes(20)
                .count(100)
                .reportTtlMs(10000L)
                .timeoutMs(5000L)
                .build();
        pubstackEventHandler = new PubstackEventHandler(properties, true, "http://example.com", jacksonMapper,
                httpClient, vertx);

        // when
        pubstackEventHandler.handle(SetuidEvent.builder().bidder("bidder1").build());

        // then
        verify(httpClient).request(any(), anyString(), any(), (byte[]) any(), anyLong());
    }

    @Test
    public void handleShouldSendEventsWhenMaxCountEventsBufferExceeds() {
        // given
        final PubstackAnalyticsProperties properties = PubstackAnalyticsProperties.builder()
                .endpoint("http://endpoint.com")
                .scopeId("scopeId")
                .sizeBytes(20000)
                .count(1)
                .reportTtlMs(10000L)
                .timeoutMs(5000L)
                .build();
        pubstackEventHandler = new PubstackEventHandler(properties, true, "http://example.com", jacksonMapper,
                httpClient, vertx);

        // when
        pubstackEventHandler.handle(SetuidEvent.builder().bidder("bidder1").build());
        pubstackEventHandler.handle(SetuidEvent.builder().bidder("bidder2").build());

        // then
        verify(httpClient).request(any(), anyString(), any(), (byte[]) any(), anyLong());
    }

    @Test
    public void handleShouldBeAbleToEncodeAuctionEvent() {
        // given
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .uidsCookie(mock(UidsCookie.class))
                        .timeout(mock(Timeout.class))
                        .txnLog(mock(TxnLog.class))
                        .deepDebugLog(mock(DeepDebugLog.class))
                        .build())
                .build();

        // when and then
        assertThatCode(() -> pubstackEventHandler.handle(event))
                .doesNotThrowAnyException();
    }

    @Test
    public void sendEventsShouldSendEventsAndResetSendConditionParameters() {
        // given
        given(httpClient.request(any(), anyString(), any(), (byte[]) any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, null)));

        pubstackEventHandler.handle(SetuidEvent.builder().bidder("bidder1").build());

        // when
        pubstackEventHandler.reportEvents();

        // then
        verify(vertx).cancelTimer(anyLong());
        // one time in constructor and second after the send request
        verify(vertx, times(2)).setTimer(anyLong(), any());
        final AtomicLong byteSize = (AtomicLong) ReflectionTestUtils.getField(pubstackEventHandler, "byteSize");
        assertThat(byteSize.get()).isEqualTo(0);
        final Long currentTimerId = (Long) ReflectionTestUtils.getField(pubstackEventHandler,
                "reportTimerId");
        assertThat(currentTimerId).isEqualTo(2);
    }

    @Test
    public void updateConfigShouldSetNewValuesToEndpointScopeIdAndEnabledConfigs() {
        // given and when
        pubstackEventHandler.updateConfig(false, "newEndpoint", "newScope");

        // then
        final Boolean enabled = (Boolean) ReflectionTestUtils.getField(pubstackEventHandler, "enabled");
        final String endpoint = (String) ReflectionTestUtils.getField(pubstackEventHandler, "endpoint");
        final String newScope = (String) ReflectionTestUtils.getField(pubstackEventHandler, "scopeId");
        assertThat(enabled).isFalse();
        assertThat(endpoint).isEqualTo("newEndpoint");
        assertThat(newScope).isEqualTo("newScope");
    }

    @Test
    public void updateConfigShouldCancelReportTimerOnDisablingHandler() {
        // given and when
        pubstackEventHandler.updateConfig(false, "newEndpoint", "newScope");

        // then
        verify(vertx).setTimer(anyLong(), any());
        verify(vertx).cancelTimer(anyLong());
    }

    @Test
    public void updateConfigShouldSetReportTimerOnDisablingHandler() {
        // given and when
        pubstackEventHandler.updateConfig(false, "newEndpoint", "newScope");
        pubstackEventHandler.updateConfig(true, "newEndpoint", "newScope");

        // then
        // first time on handler creation, second time on enabling after it was disabled
        verify(vertx, times(2)).setTimer(anyLong(), any());
        verify(vertx).cancelTimer(anyLong());
    }
}
