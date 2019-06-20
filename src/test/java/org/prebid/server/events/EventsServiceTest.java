package org.prebid.server.events;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;

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
    private ApplicationSettings applicationSettings;

    private EventsService eventsService;

    private Timeout timeout;

    @Before
    public void setUp() {
        eventsService = new EventsService(applicationSettings, "http://external.org");

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500);
    }

    @Test
    public void isEventEnabledShouldReturnFalseWhenAccountsEventEnabledIsFalse() {
        // given
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(false).build()));

        // when
        final Future<Boolean> future = eventsService.isEventsEnabled("publisherId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isFalse();
    }

    @Test
    public void isEventEnabledShouldReturnFalseWhenAccountsEventEnabledIsTrue() {
        // given
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(true).build()));

        // when
        final Future<Boolean> future = eventsService.isEventsEnabled("publisherId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isTrue();
    }

    @Test
    public void isEventEnabledShouldReturnFalseWhenApplicationSettingsReturnsEmptyResult() {
        // given
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found")));

        // when
        final Future<Boolean> future = eventsService.isEventsEnabled("publisherId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isFalse();
    }

    @Test
    public void isEventEnabledShouldReturnFalseWhenApplicationSettingFailed() {
        // given
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("exception")));

        // when
        final Future<Boolean> future = eventsService.isEventsEnabled("publisherId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isFalse();
    }

    @Test
    public void createEventsShouldReturnNewEvent() {
        // when
        final Events events = eventsService.createEvent("bidId", "bidder");

        // then
        assertThat(events).isEqualTo(Events.of("http://external.org/event?type=win&bidid=bidId&bidder=bidder",
                "http://external.org/event?type=view&bidid=bidId&bidder=bidder"));
    }
}
