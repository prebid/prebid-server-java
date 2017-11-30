package org.rtb.vexing.settings;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.adapter.PreBidRequestException;
import org.rtb.vexing.settings.model.Account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        assertThatNullPointerException().isThrownBy(() -> cachingApplicationSettings.getAccountById(null));
    }

    @Test
    public void getAccountByIdShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
        given(applicationSettings.getAccountById(eq("accountId")))
                .willReturn(Future.succeededFuture(Account.builder().id("accountId").priceGranularity("med").build()));

        // when
        final Future<Account> future = cachingApplicationSettings.getAccountById("accountId");
        cachingApplicationSettings.getAccountById("accountId");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(Account.builder().id("accountId").priceGranularity("med").build());
        verify(applicationSettings).getAccountById(eq("accountId"));
        verifyNoMoreInteractions(applicationSettings);
    }

    @Test
    public void getAccountByIdShouldPropagateFailure() {
        // given
        given(applicationSettings.getAccountById(anyString()))
                .willReturn(Future.failedFuture(new PreBidRequestException("Not found")));

        // when
        final Future<Account> future = cachingApplicationSettings.getAccountById("accountId");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidRequestException.class)
                .hasMessage("Not found");
    }

    @Test
    public void getAdUnitConfigByIdShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> cachingApplicationSettings.getAdUnitConfigById(null));
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
        given(applicationSettings.getAdUnitConfigById(eq("adUnitConfigId")))
                .willReturn(Future.succeededFuture("config"));

        // when
        final Future<String> future = cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId");
        cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("config");
        verify(applicationSettings).getAdUnitConfigById(eq("adUnitConfigId"));
        verifyNoMoreInteractions(applicationSettings);
    }

    @Test
    public void getAdUnitConfigByIdShouldPropagateFailure() {
        // given
        given(applicationSettings.getAdUnitConfigById(anyString()))
                .willReturn(Future.failedFuture(new PreBidRequestException("Not found")));

        // when
        final Future<String> future = cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidRequestException.class)
                .hasMessage("Not found");
    }
}