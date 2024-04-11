package org.prebid.server.settings;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CachingApplicationSettingsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings delegateSettings;
    @Mock
    private Metrics metrics;

    private CachingApplicationSettings target;

    private Timeout timeout;

    @Before
    public void setUp() {
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500L);

        target = new CachingApplicationSettings(
                delegateSettings,
                new SettingsCache(360, 100),
                new SettingsCache(360, 100),
                new SettingsCache(360, 100),
                metrics,
                360,
                100);
    }

    @Test
    public void getAccountByIdShouldReturnResultFromCacheOnSuccessiveCallsWhenAccountIdIsNull() {
        // given
        final Account account = Account.empty("");
        given(delegateSettings.getAccountById(eq(""), same(timeout)))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> future = target.getAccountById(null, timeout);
        target.getAccountById("", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
        verify(delegateSettings).getAccountById(eq(""), same(timeout));
        verifyNoMoreInteractions(delegateSettings);
    }

    @Test
    public void getAccountByIdShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("med")
                        .build())
                .build();
        given(delegateSettings.getAccountById(eq("accountId"), same(timeout)))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> future = target.getAccountById("accountId", timeout);
        target.getAccountById("accountId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
        verify(delegateSettings).getAccountById(eq("accountId"), same(timeout));
        verifyNoMoreInteractions(delegateSettings);
    }

    @Test
    public void getAccountByIdShouldReturnResultFromSeparateCallWhenCacheWasInvalidatedForAccount() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("med")
                        .build())
                .build();
        given(delegateSettings.getAccountById(eq("accountId"), same(timeout)))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> future = target.getAccountById("accountId", timeout);
        target.invalidateAccountCache(account.getId());
        target.getAccountById("accountId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
        verify(delegateSettings, times(2)).getAccountById(eq("accountId"), same(timeout));
    }

    @Test
    public void getAccountByIdShouldReturnResultFromSeparateCallWhenCacheWasInvalidatedForAllAccounts() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("med")
                        .build())
                .build();
        given(delegateSettings.getAccountById(eq("accountId"), same(timeout)))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> future = target.getAccountById("accountId", timeout);
        target.invalidateAllAccountCache();
        target.getAccountById("accountId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
        verify(delegateSettings, times(2)).getAccountById(eq("accountId"), same(timeout));
    }

    @Test
    public void getAccountByIdShouldPropagateFailure() {
        // given
        given(delegateSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        final Future<Account> future =
                target.getAccountById("accountId", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getAccountByIdShouldCachePreBidException() {
        // given
        given(delegateSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        target.getAccountById("accountId", timeout);
        target.getAccountById("accountId", timeout);
        target.getAccountById("accountId", timeout);
        final Future<Account> lastFuture = target
                .getAccountById("accountId", timeout);

        // then
        verify(delegateSettings).getAccountById(anyString(), any());
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getAccountByIdShouldThrowSeparatePreBidExceptionWhenCacheWasInvalidatedForAccount() {
        // given
        given(delegateSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        target.getAccountById("accountId", timeout);
        target.invalidateAccountCache("accountId");
        final Future<Account> lastFuture = target
                .getAccountById("accountId", timeout);

        // then
        verify(delegateSettings, times(2)).getAccountById(eq("accountId"), same(timeout));
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getAccountByIdShouldThrowSeparatePreBidExceptionWhenCacheWasInvalidatedForAllAccounts() {
        // given
        given(delegateSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        target.getAccountById("accountId", timeout);
        target.invalidateAllAccountCache();
        final Future<Account> lastFuture = target
                .getAccountById("accountId", timeout);

        // then
        verify(delegateSettings, times(2)).getAccountById(eq("accountId"), same(timeout));
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getAccountByIdShouldNotCacheNotPreBidException() {
        // given
        given(delegateSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("error")));

        // when
        target.getAccountById("accountId", timeout);
        target.getAccountById("accountId", timeout);
        final Future<Account> lastFuture = target
                .getAccountById("accountId", timeout);

        // then
        verify(delegateSettings, times(3)).getAccountById(anyString(), any());
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");
    }

    @Test
    public void getAccountByIdShouldUpdateMetrics() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("med")
                        .build())
                .build();
        given(delegateSettings.getAccountById(eq("accountId"), same(timeout)))
                .willReturn(Future.succeededFuture(account));

        // when
        target.getAccountById("accountId", timeout);
        target.getAccountById("accountId", timeout);

        // then
        verify(metrics).updateSettingsCacheEventMetric(eq(MetricName.account), eq(MetricName.miss));
        verify(metrics).updateSettingsCacheEventMetric(eq(MetricName.account), eq(MetricName.hit));
    }

    @Test
    public void getCategoriesShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
        given(delegateSettings.getCategories(eq("adServer"), eq("publisher"), same(timeout)))
                .willReturn(Future.succeededFuture(singletonMap("iab", "id")));

        // when
        final Future<Map<String, String>> future
                = target.getCategories("adServer", "publisher", timeout);
        target.getCategories("adServer", "publisher", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(singletonMap("iab", "id"));
        verify(delegateSettings).getCategories(eq("adServer"), eq("publisher"), same(timeout));
        verifyNoMoreInteractions(delegateSettings);
    }

    @Test
    public void getCategoriesShouldPropagateFailure() {
        // given
        given(delegateSettings.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        final Future<Map<String, String>> future =
                target.getCategories("adServer", "publisher", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getCategoriesShouldCachePreBidException() {
        // given
        given(delegateSettings.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        target.getCategories("adServer", "publisher", timeout);
        target.getCategories("adServer", "publisher", timeout);
        target.getCategories("adServer", "publisher", timeout);
        final Future<Map<String, String>> lastFuture =
                target.getCategories("adServer", "publisher", timeout);

        // then
        verify(delegateSettings).getCategories(anyString(), anyString(), any());
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getCategoriesShouldNotCacheNotPreBidException() {
        // given
        given(delegateSettings.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.failedFuture(new TimeoutException("timeout")));

        // when

        target.getCategories("adServer", "publisher", timeout);
        target.getCategories("adServer", "publisher", timeout);
        final Future<Map<String, String>> lastFuture =
                target.getCategories("adServer", "publisher", timeout);

        // then
        verify(delegateSettings, times(3)).getCategories(anyString(), anyString(), any());
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(TimeoutException.class)
                .hasMessage("timeout");
    }

    @Test
    public void getStoredDataShouldReturnResultOnSuccessiveCalls() {
        // given
        given(delegateSettings.getStoredData(any(), eq(singleton("reqid")), eq(singleton("impid")), same(timeout)))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        singletonMap("reqid", "json"), singletonMap("impid", "json2"), emptyList())));

        // when
        final Future<StoredDataResult> future =
                target.getStoredData("1001", singleton("reqid"), singleton("impid"), timeout);
        // second call
        target.getStoredData("1001", singleton("reqid"), singleton("impid"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(StoredDataResult.of(
                singletonMap("reqid", "json"), singletonMap("impid", "json2"), emptyList()));
        verify(delegateSettings)
                .getStoredData(eq("1001"), eq(singleton("reqid")), eq(singleton("impid")), same(timeout));
        verifyNoMoreInteractions(delegateSettings);
    }

    @Test
    public void getStoredDataShouldPropagateFailure() {
        // given
        given(delegateSettings.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("error")));

        // when
        final Future<StoredDataResult> future =
                target.getStoredData(null, singleton("id"), emptySet(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorsOnNotSuccessiveCallToCacheAndErrorInDelegateCall() {
        // given
        given(delegateSettings.getStoredData(any(), eq(singleton("id")), eq(emptySet()), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        emptyMap(), emptyMap(), singletonList("error"))));

        // when
        final Future<StoredDataResult> future =
                target.getStoredData(null, singleton("id"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error")));
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfAccountDiffers() {
        // given
        given(delegateSettings.getStoredData(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("reqid", "json"), emptyMap(), emptyList())))
                .willReturn(Future.failedFuture("error"));

        // when
        target.getStoredData("1001", singleton("reqid"), emptySet(), timeout);
        // second call
        final Future<StoredDataResult> future =
                target.getStoredData("1002", singleton("reqid"), emptySet(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("error");
        verify(delegateSettings)
                .getStoredData(eq("1001"), eq(singleton("reqid")), eq(emptySet()), same(timeout));
        verify(delegateSettings)
                .getStoredData(eq("1002"), eq(singleton("reqid")), eq(emptySet()), same(timeout));
        verifyNoMoreInteractions(delegateSettings);
    }

    @Test
    public void getStoredResponseShouldPropagateFailure() {
        // given
        given(delegateSettings.getStoredResponses(anySet(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("error")));

        // when
        final Future<StoredResponseDataResult> future = target
                .getStoredResponses(singleton("id"), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");
    }
}
