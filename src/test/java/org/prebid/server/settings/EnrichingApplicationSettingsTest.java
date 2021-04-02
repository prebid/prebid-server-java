package org.prebid.server.settings;

import io.vertx.core.Future;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class EnrichingApplicationSettingsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings delegate;

    private EnrichingApplicationSettings enrichingApplicationSettings;

    @Mock
    private Timeout timeout;

    @Test
    public void getAccountByIdShouldOmitMergingWhenDefaultAccountIsNull() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(delegate, null);

        final Account returnedAccount = Account.builder().build();
        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(returnedAccount));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).isSucceeded();
        assertThat(accountFuture.result()).isSameAs(returnedAccount);

        verify(delegate).getAccountById(eq("123"), eq(timeout));
    }

    @Test
    public void getAccountByIdShouldOmitMergingWhenDefaultAccountIsEmpty() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                delegate,
                Account.builder()
                        .bannerCacheTtl(null)
                        .analyticsSamplingFactor(null)
                        .build());

        final Account returnedAccount = Account.builder().build();
        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(returnedAccount));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).isSucceeded();
        assertThat(accountFuture.result()).isSameAs(returnedAccount);

        verify(delegate).getAccountById(eq("123"), eq(timeout));
    }

    @Test
    public void getAccountByIdShouldMergeAccountWithDefaultAccount() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                delegate,
                Account.builder()
                        .bannerCacheTtl(100)
                        .analyticsSamplingFactor(50)
                        .build());

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(Account.builder()
                .id("123")
                .videoCacheTtl(200)
                .enforceCcpa(true)
                .build()));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).succeededWith(Account.builder()
                .id("123")
                .bannerCacheTtl(100)
                .videoCacheTtl(200)
                .enforceCcpa(true)
                .analyticsSamplingFactor(50)
                .build());
    }

    @Test
    public void getAccountByIdShouldReturnDefaultAccountWhenDelegateFailed() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                delegate,
                Account.builder()
                        .bannerCacheTtl(100)
                        .analyticsSamplingFactor(50)
                        .build());

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.failedFuture("Exception"));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).succeededWith(Account.builder()
                .id("123")
                .bannerCacheTtl(100)
                .analyticsSamplingFactor(50)
                .build());
    }

    @Test
    public void getAccountByIdShouldPassOnFailureWhenDefaultAccountIsEmpty() {
        // given
        enrichingApplicationSettings = new EnrichingApplicationSettings(
                delegate,
                Account.builder()
                        .bannerCacheTtl(null)
                        .analyticsSamplingFactor(null)
                        .build());

        given(delegate.getAccountById(anyString(), any())).willReturn(Future.failedFuture("Exception"));

        // when
        final Future<Account> accountFuture = enrichingApplicationSettings.getAccountById("123", timeout);

        // then
        assertThat(accountFuture).isFailed();
    }
}
