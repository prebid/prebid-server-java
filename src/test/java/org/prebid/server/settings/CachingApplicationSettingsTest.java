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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
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

    private Timeout timeout;

    @Before
    public void setUp() {
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500L);

        cachingApplicationSettings = new CachingApplicationSettings(applicationSettings, 360, 100);
    }

    @Test
    public void getAccountByIdShouldReturnResultFromCacheOnSuccessiveCalls() {
        // given
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
    public void getStoredDataShouldReturnResultOnSuccessiveCalls() {
        // given
        given(applicationSettings.getStoredData(eq(singleton("reqid")), eq(singleton("impid")), same(timeout)))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        singletonMap("reqid", "json"), singletonMap("impid", "json2"), emptyList())));

        // when
        final Future<StoredDataResult> future =
                cachingApplicationSettings.getStoredData(singleton("reqid"), singleton("impid"), timeout);
        cachingApplicationSettings.getStoredData(singleton("reqid"), singleton("impid"), timeout); // second call

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(StoredDataResult.of(
                singletonMap("reqid", "json"), singletonMap("impid", "json2"), emptyList()));
        verify(applicationSettings).getStoredData(eq(singleton("reqid")), eq(singleton("impid")), same(timeout));
        verifyNoMoreInteractions(applicationSettings);
    }

    @Test
    public void getStoredDataShouldPropagateFailure() {
        // given
        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("error")));

        // when
        final Future<StoredDataResult> future =
                cachingApplicationSettings.getStoredData(singleton("id"), emptySet(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorsOnNotSuccessiveCallToCacheAndErrorInDelegateCall() {
        // given
        given(applicationSettings.getStoredData(eq(singleton("id")), eq(emptySet()), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        emptyMap(), emptyMap(), singletonList("error"))));

        // when
        final Future<StoredDataResult> future =
                cachingApplicationSettings.getStoredData(singleton("id"), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error")));
    }
}
