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
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

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
    private ApplicationSettings applicationSettings;

    private CachingApplicationSettings cachingApplicationSettings;

    private Timeout timeout;

    @Before
    public void setUp() {
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500L);

        cachingApplicationSettings = new CachingApplicationSettings(applicationSettings, new SettingsCache(360, 100),
                new SettingsCache(360, 100), new SettingsCache(360, 100), 360, 100);
    }

    @Test
    public void getAccountByIdShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
        final Account account = Account.builder().id("accountId").priceGranularity("med").build();
        given(applicationSettings.getAccountById(eq("accountId"), same(timeout)))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> future = cachingApplicationSettings.getAccountById("accountId", timeout);
        cachingApplicationSettings.getAccountById("accountId", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
        verify(applicationSettings).getAccountById(eq("accountId"), same(timeout));
        verifyNoMoreInteractions(applicationSettings);
    }

    @Test
    public void getAccountByIdShouldPropagateFailure() {
        // given
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        final Future<Account> future =
                cachingApplicationSettings.getAccountById("accountId", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getAccountByIdShouldCachePreBidException() {
        // given
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        cachingApplicationSettings.getAccountById("accountId", timeout);
        cachingApplicationSettings.getAccountById("accountId", timeout);
        cachingApplicationSettings.getAccountById("accountId", timeout);
        final Future<Account> lastFuture = cachingApplicationSettings
                .getAccountById("accountId", timeout);

        // then
        verify(applicationSettings).getAccountById(anyString(), any());
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getAccountByIdShouldNotCacheNotPreBidException() {
        // given
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("error")));

        // when
        cachingApplicationSettings.getAccountById("accountId", timeout);
        cachingApplicationSettings.getAccountById("accountId", timeout);
        final Future<Account> lastFuture = cachingApplicationSettings
                .getAccountById("accountId", timeout);

        // then
        verify(applicationSettings, times(3)).getAccountById(anyString(), any());
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
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
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        final Future<String> future =
                cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getAdUnitConfigByIdShouldCachePreBidException() {
        // given
        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error")));

        // when
        cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);
        cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);
        cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);
        final Future<String> lastFuture =
                cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);

        // then
        verify(applicationSettings).getAdUnitConfigById(anyString(), any());
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("error");
    }

    @Test
    public void getAdUnitConfigByIdShouldNotCacheNotPreBidException() {
        // given
        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("error")));

        // when

        cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);
        cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);
        final Future<String> lastFuture =
                cachingApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);

        // then
        verify(applicationSettings, times(3)).getAdUnitConfigById(anyString(), any());
        assertThat(lastFuture.failed()).isTrue();
        assertThat(lastFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");
    }

    @Test
    public void getStoredDataShouldReturnResultOnSuccessiveCalls() {
        // given
        given(applicationSettings.getStoredData(any(), eq(singleton("reqid")), eq(singleton("impid")), same(timeout)))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        singletonMap("reqid", "json"), singletonMap("impid", "json2"), emptyList())));

        // when
        final Future<StoredDataResult> future =
                cachingApplicationSettings.getStoredData("1001", singleton("reqid"), singleton("impid"), timeout);
        // second call
        cachingApplicationSettings.getStoredData("1001", singleton("reqid"), singleton("impid"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(StoredDataResult.of(
                singletonMap("reqid", "json"), singletonMap("impid", "json2"), emptyList()));
        verify(applicationSettings)
                .getStoredData(eq("1001"), eq(singleton("reqid")), eq(singleton("impid")), same(timeout));
        verifyNoMoreInteractions(applicationSettings);
    }

    @Test
    public void getStoredDataShouldPropagateFailure() {
        // given
        given(applicationSettings.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("error")));

        // when
        final Future<StoredDataResult> future =
                cachingApplicationSettings.getStoredData(null, singleton("id"), emptySet(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorsOnNotSuccessiveCallToCacheAndErrorInDelegateCall() {
        // given
        given(applicationSettings.getStoredData(any(), eq(singleton("id")), eq(emptySet()), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        emptyMap(), emptyMap(), singletonList("error"))));

        // when
        final Future<StoredDataResult> future =
                cachingApplicationSettings.getStoredData(null, singleton("id"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error")));
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfAccountDiffers() {
        // given
        given(applicationSettings.getStoredData(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("reqid", "json"), emptyMap(), emptyList())))
                .willReturn(Future.failedFuture("error"));

        // when
        cachingApplicationSettings.getStoredData("1001", singleton("reqid"), emptySet(), timeout);
        // second call
        final Future<StoredDataResult> future =
                cachingApplicationSettings.getStoredData("1002", singleton("reqid"), emptySet(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("error");
        verify(applicationSettings)
                .getStoredData(eq("1001"), eq(singleton("reqid")), eq(emptySet()), same(timeout));
        verify(applicationSettings)
                .getStoredData(eq("1002"), eq(singleton("reqid")), eq(emptySet()), same(timeout));
        verifyNoMoreInteractions(applicationSettings);
    }

    @Test
    public void getStoredResponseShouldPropagateFailure() {
        // given
        given(applicationSettings.getStoredResponses(anySet(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("error")));

        // when
        final Future<StoredResponseDataResult> future = cachingApplicationSettings
                .getStoredResponses(singleton("id"), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");
    }
}
