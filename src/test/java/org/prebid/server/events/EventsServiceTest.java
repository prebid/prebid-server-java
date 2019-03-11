package org.prebid.server.events;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.events.EventsService;
import org.prebid.server.events.account.AccountEventsService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.response.Events;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class EventsServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AccountEventsService accountEventsService;

    private Clock clock;

    private Timeout timeout;

    private EventsService eventsService;

    @Before
    public void setUp() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500);
        eventsService = new EventsService(accountEventsService, "http://external.org");
    }

    @Test
    public void shouldReturnEmptyEventWhenEventsNotEnabledForPublisherId() {
        // given
        given(accountEventsService.eventsEnabled(anyString(), any())).willReturn(Future.succeededFuture(false));

        // when
        final Future<Events> events = eventsService.createEvents("publisherId", "bidId", "bidder", timeout);

        // then
        assertThat(events.succeeded()).isTrue();
        assertThat(events.result()).isEqualTo(Events.empty());
    }

    @Test
    public void shouldReturnEmptyEventsWhenAccountEventsServiceRespondsWithFailedFuture() {
        // given
        given(accountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found")));

        // when
        final Future<Events> events = eventsService.createEvents("publisherId", "bidId", "bidder", timeout);

        // then
        assertThat(events.succeeded()).isTrue();
        assertThat(events.result()).isEqualTo(Events.empty());
    }

    @Test
    public void shouldReturnNewEventsObject() {
        //given
        given(accountEventsService.eventsEnabled(anyString(), any())).willReturn(Future.succeededFuture(true));

        // when
        final Future<Events> events = eventsService.createEvents("publisherId", "bidId", "bidder", timeout);

        // then
        assertThat(events.succeeded()).isTrue();
        assertThat(events.result()).isEqualTo(Events.of("http://external.org/event?type=win&bidid=bidId&bidder=bidder",
                "http://external.org/event?type=view&bidid=bidId&bidder=bidder"));
    }
}
