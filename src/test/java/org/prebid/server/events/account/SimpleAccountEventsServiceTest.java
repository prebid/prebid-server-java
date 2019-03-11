package org.prebid.server.events.account;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class SimpleAccountEventsServiceTest {

    private SimpleAccountEventsService simpleAccountEventsService;

    private Clock clock;

    private Timeout timeout;

    @Before
    public void setUp() {
        simpleAccountEventsService = new SimpleAccountEventsService(singletonList("1001"));
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);
    }

    @Test
    public void shouldReturnSucceededFutureWithTrueWhenAccountIdIsInEnabledList() {
        // when
        final Future<Boolean> eventsEnabledFuture = simpleAccountEventsService.eventsEnabled("1001", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isTrue();
    }

    @Test
    public void shouldReturnSucceededFutureWithFalseWhenAccountIdIsNotInEnabledList() {
        // when
        final Future<Boolean> eventsEnabledFuture = simpleAccountEventsService.eventsEnabled("1002", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isFalse();
    }

    @Test
    public void shouldReturnFailedFutureWhenTimeoutRemains() {
        timeout = new TimeoutFactory(clock).create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        // when
        final Future<Boolean> eventsEnabledFuture = simpleAccountEventsService.eventsEnabled("1001", timeout);

        // then
        assertThat(eventsEnabledFuture.failed()).isTrue();
        assertThat(eventsEnabledFuture.cause()).isInstanceOf(TimeoutException.class).hasMessage("Timeout has been exceeded");
    }
}
