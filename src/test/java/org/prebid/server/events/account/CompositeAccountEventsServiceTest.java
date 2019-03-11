package org.prebid.server.events.account;

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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class CompositeAccountEventsServiceTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SettingsAccountEventsService settingsAccountEventsService;

    @Mock
    private SimpleAccountEventsService simpleAccountEventsService;

    private Timeout timeout;

    private CompositeAccountEventsService compositeAccountEventsService;

    @Before
    public void init() {
        compositeAccountEventsService = new CompositeAccountEventsService(asList(settingsAccountEventsService,
                simpleAccountEventsService));
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(5000L);
    }

    @Test
    public void shouldReturnSucceededFutureTrueFromSettingsAccountEventsService() {
        // given
        given(settingsAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.succeededFuture(true));

        // when
        final Future<Boolean> eventsEnabledFuture = compositeAccountEventsService.eventsEnabled("publisherId", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isTrue();
    }

    @Test
    public void shouldReturnSucceededFutureFalseFromSettingsAccountEventsService() {
        // given
        given(settingsAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.succeededFuture(false));

        // when
        final Future<Boolean> eventsEnabledFuture = compositeAccountEventsService.eventsEnabled("publisherId", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isFalse();
    }

    @Test
    public void shouldReturnResultFromSimpleAccountEventsServiceWhenSettingsReturnedNull() {
        // given
        given(settingsAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.succeededFuture(null));
        given(simpleAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.succeededFuture(true));

        // when
        final Future<Boolean> eventsEnabledFuture = compositeAccountEventsService.eventsEnabled("publisherId", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isTrue();
    }

    @Test
    public void shouldReturnResultFromSimpleAccountEventsServiceWhenSettingsThrowException() {
        // given
        given(settingsAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found")));
        given(simpleAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.succeededFuture(true));


        // when
        final Future<Boolean> eventsEnabledFuture = compositeAccountEventsService.eventsEnabled("pulbisherId", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isTrue();
    }

    @Test
    public void shouldReturnFalseWhenBothEventsServicesReturnedFailedFuture() {
        // given
        given(settingsAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found jdbc")));
        given(simpleAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found simple")));

        // when
        final Future<Boolean> eventsEnabledFuture = compositeAccountEventsService.eventsEnabled("publisherId", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isFalse();
    }

    @Test
    public void shouldReturnSucceededFutureWithFalseWithSingleEventsServiceWhichReturnsFailedFuture() {
        // given
        compositeAccountEventsService = new CompositeAccountEventsService(singletonList(settingsAccountEventsService));
        given(settingsAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found jdbc")));

        // when
        final Future<Boolean> eventsEnabledFuture = compositeAccountEventsService.eventsEnabled("publisherId", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isFalse();
    }

    @Test
    public void shouldReturnSucceededFutureWithTrueWithSingleEventsService() {
        // given
        compositeAccountEventsService = new CompositeAccountEventsService(singletonList(settingsAccountEventsService));
        given(settingsAccountEventsService.eventsEnabled(anyString(), any()))
                .willReturn(Future.succeededFuture(true));

        // when
        final Future<Boolean> eventsEnabledFuture = compositeAccountEventsService.eventsEnabled("publisherId", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isTrue();
    }
}
