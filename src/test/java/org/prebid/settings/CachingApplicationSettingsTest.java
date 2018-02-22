package org.prebid.settings;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.exception.PreBidException;
import org.prebid.execution.GlobalTimeout;
import org.prebid.settings.model.Account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CachingApplicationSettingsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;

    private CachingApplicationSettings cachingApplicationSettings;

    @Before
    public void setUp() {
        cachingApplicationSettings = new CachingApplicationSettings(applicationSettings, 360, 100);
    }

    @Test
    public void creationShouldFailOnInvalidArguments() {
        assertThatNullPointerException().isThrownBy(() -> new CachingApplicationSettings(null, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new CachingApplicationSettings(applicationSettings, 0, 1))
                .withMessage("ttl and size must be positive");
        assertThatIllegalArgumentException().isThrownBy(() -> new CachingApplicationSettings(applicationSettings, 1, 0))
                .withMessage("ttl and size must be positive");
    }

    @Test
    public void getAccountByIdShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> cachingApplicationSettings.getAccountById(null, null));
        assertThatNullPointerException().isThrownBy(() -> cachingApplicationSettings.getAccountById("accountId", null));
    }

    @Test
    public void getAccountByIdShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
        final GlobalTimeout timeout = GlobalTimeout.create(500);
        given(applicationSettings.getAccountById(eq("accountId"), same(timeout)))
                .willReturn(Future.succeededFuture(Account.of("accountId", "med")));

        // when
        final Future<Account> future = cachingApplicationSettings.getAccountById("accountId", timeout);
        cachingApplicationSettings.getAccountById("accountId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(Account.of("accountId", "med"));
        verify(applicationSettings).getAccountById(eq("accountId"), same(timeout));
        verifyNoMoreInteractions(applicationSettings);
    }

    @Test
    public void getAccountByIdShouldPropagateFailure() {
        // given
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        final Future<Account> future =
                cachingApplicationSettings.getAccountById("accountId", GlobalTimeout.create(500));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Not found");
    }

    @Test
    public void getAdUnitConfigByIdShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> cachingApplicationSettings.getAdUnitConfigById(null, null));
        assertThatNullPointerException().isThrownBy(
                () -> cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", null));
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
        final GlobalTimeout timeout = GlobalTimeout.create(500);
        given(applicationSettings.getAdUnitConfigById(eq("adUnitConfigId"), same(timeout)))
                .willReturn(Future.succeededFuture("config"));

        // when
        final Future<String> future = cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);
        cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("config");
        verify(applicationSettings).getAdUnitConfigById(eq("adUnitConfigId"), same(timeout));
        verifyNoMoreInteractions(applicationSettings);
    }

    @Test
    public void getAdUnitConfigByIdShouldPropagateFailure() {
        // given
        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        final Future<String> future =
                cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", GlobalTimeout.create(500));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Not found");
    }
}