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
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class SettingsAccountEventsServiceTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;

    private SettingsAccountEventsService settingsAccountEventsService;

    private Clock clock;

    private Timeout timeout;

    @Before
    public void setUp() {
        settingsAccountEventsService = new SettingsAccountEventsService(applicationSettings);
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);
    }

    @Test
    public void shouldReturnSucceededFutureWhenApplicationSettingsRespondWithAccountWithEnabledTrue() {
        // given
        given(applicationSettings.getOrtb2AccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.of(null, null, null, null, true)));

        // when
        final Future<Boolean> eventsEnabledFuture = settingsAccountEventsService.eventsEnabled("1001", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isTrue();
    }

    @Test
    public void shouldReturnSucceededFutureWhenApplicationSettingsRespondWithAccountWithEnabledFalse() {
        // given
        given(applicationSettings.getOrtb2AccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.of(null, null, null, null, false)));

        // when
        final Future<Boolean> eventsEnabledFuture = settingsAccountEventsService.eventsEnabled("1001", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isFalse();
    }

    @Test
    public void shouldReturnSucceededFutureNullTtlWhenApplicationSettingsRespondWithNull() {
        // given
        given(applicationSettings.getOrtb2AccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(null));

        // when
        final Future<Boolean> eventsEnabledFuture = settingsAccountEventsService.eventsEnabled("1001", timeout);

        // then
        assertThat(eventsEnabledFuture.succeeded()).isTrue();
        assertThat(eventsEnabledFuture.result()).isEqualTo(null);
    }

    @Test
    public void shouldReturnFailedFutureWhenApplicationSettingsRespondWithFailedFuture() {
        // given
        given(applicationSettings.getOrtb2AccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found")));

        // when
        final Future<Boolean> eventsEnabledFuture = settingsAccountEventsService.eventsEnabled("1001", timeout);

        // then
        assertThat(eventsEnabledFuture.failed()).isTrue();
        assertThat(eventsEnabledFuture.cause()).isInstanceOf(PreBidException.class).hasMessage("Not Found");
    }
}
