package org.prebid.server.cache.account;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.cache.model.CacheTtl;
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

public class SettingsAccountCacheServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;

    private SettingsAccountCacheService settingsAccountCacheService;

    private Clock clock;

    private Timeout timeout;

    @Before
    public void setUp() {
        settingsAccountCacheService = new SettingsAccountCacheService(applicationSettings);
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);
    }

    @Test
    public void shouldReturnSucceededFutureWhenApplicationSettingsRespondWithAccountWithCacheTTL() {
        // given
        given(applicationSettings.getOrtb2AccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.of(null, null, 100, 100, null)));

        // when
        final Future<CacheTtl> cacheTtlFuture = settingsAccountCacheService.getCacheTtlByAccountId("1001", timeout);

        // then
        assertThat(cacheTtlFuture.succeeded()).isTrue();
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.of(100, 100));
    }

    @Test
    public void shouldReturnSucceededFutureWithEmptyCacheTtlWhenApplicationSettingsRespondWithNull() {
        // given
        given(applicationSettings.getOrtb2AccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(null));

        // when
        final Future<CacheTtl> cacheTtlFuture = settingsAccountCacheService.getCacheTtlByAccountId("1001", timeout);

        // then
        assertThat(cacheTtlFuture.succeeded()).isTrue();
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.empty());
    }

    @Test
    public void shouldReturnFailedFutureWhenApplicationSettingsRespondWithFailedFuture() {
        // given
        given(applicationSettings.getOrtb2AccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found")));

        // when
        final Future<CacheTtl> cacheTtlFuture = settingsAccountCacheService.getCacheTtlByAccountId("1001", timeout);

        // then
        assertThat(cacheTtlFuture.failed()).isTrue();
        assertThat(cacheTtlFuture.cause()).isInstanceOf(PreBidException.class).hasMessage("Not Found");
    }
}
