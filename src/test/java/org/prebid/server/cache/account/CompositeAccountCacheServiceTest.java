package org.prebid.server.cache.account;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.cache.model.CacheTtl;
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

public class CompositeAccountCacheServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SettingsAccountCacheService settingsAccountCacheService;

    @Mock
    private SimpleAccountCacheService simpleAccountCacheService;

    private Timeout timeout;

    private CompositeAccountCacheService compositeAccountCacheService;

    @Before
    public void init() {
        compositeAccountCacheService = new CompositeAccountCacheService(asList(settingsAccountCacheService,
                simpleAccountCacheService));
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(5000L);
    }

    @Test
    public void shouldReturnResultFromSettingsAccountCacheService() {
        // given
        given(settingsAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.succeededFuture(CacheTtl.of(100, 100)));

        // when
        final Future<CacheTtl> cacheTtlFuture = compositeAccountCacheService.getCacheTtlByAccountId("publisherId", timeout);

        // then
        assertThat(cacheTtlFuture.succeeded()).isTrue();
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.of(100, 100));
    }

    @Test
    public void shouldReturnResultFromSettingsAccountCacheServiceWhenAtLeastOneCacheTtlParameterDefined() {
        // given
        given(settingsAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.succeededFuture(CacheTtl.of(100, null)));

        // when
        final Future<CacheTtl> cacheTtlFuture = compositeAccountCacheService.getCacheTtlByAccountId("publisherId", timeout);

        // then
        assertThat(cacheTtlFuture.succeeded()).isTrue();
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.of(100, null));
    }

    @Test
    public void shouldReturnResultFromSimpleAccountServiceWhenSettingsReturnedEmptyCache() {
        // given
        given(settingsAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.succeededFuture(CacheTtl.of(null, null)));
        given(simpleAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.succeededFuture(CacheTtl.of(100, 100)));

        // when
        final Future<CacheTtl> cacheTtlFuture = compositeAccountCacheService.getCacheTtlByAccountId("publisherId", timeout);

        // then
        assertThat(cacheTtlFuture.succeeded()).isTrue();
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.of(100, 100));
    }

    @Test
    public void shouldReturnResultFromSimpleAccountCacheServiceWhenSettingsThrowException() {
        // given
        given(settingsAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found")));
        given(simpleAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.succeededFuture(CacheTtl.of(100, 100)));


        // when
        final Future<CacheTtl> cacheTtlFuture = compositeAccountCacheService.getCacheTtlByAccountId("pulbisherId", timeout);

        // then
        assertThat(cacheTtlFuture.succeeded()).isTrue();
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.of(100, 100));
    }

    @Test
    public void shouldReturnResultFromSimpleAccountCacheServiceWhenSettingsReturnedNull() {
        // given
        given(settingsAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.succeededFuture(null));
        given(simpleAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.succeededFuture(CacheTtl.of(100, 100)));

        // when
        final Future<CacheTtl> cacheTtlFuture = compositeAccountCacheService.getCacheTtlByAccountId("publisherId", timeout);

        // then
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.of(100, 100));
    }

    @Test
    public void shouldReturnEmptyCacheTTLWhenBothCacheServicesReturnedFailedFuture() {
        // given
        given(settingsAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found jdbc")));
        given(simpleAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found simple")));

        // when
        final Future<CacheTtl> cacheTtlResult = compositeAccountCacheService.getCacheTtlByAccountId("publisherId", timeout);

        // then
        assertThat(cacheTtlResult.succeeded()).isTrue();
        assertThat(cacheTtlResult.result()).isEqualTo(CacheTtl.empty());
    }

    @Test
    public void shouldReturnEmptyCacheTtlWithSingleCacheServiceWhichReturnsFailedFuture() {
        // given
        compositeAccountCacheService = new CompositeAccountCacheService(singletonList(settingsAccountCacheService));
        given(settingsAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found jdbc")));

        // when
        final Future<CacheTtl> cacheTtlResult = compositeAccountCacheService.getCacheTtlByAccountId("publisherId", timeout);

        // then
        assertThat(cacheTtlResult.succeeded()).isTrue();
        assertThat(cacheTtlResult.result()).isEqualTo(CacheTtl.empty());
    }

    @Test
    public void shouldReturnSucceededFutureWithSingleCacheService() {
        // given
        compositeAccountCacheService = new CompositeAccountCacheService(singletonList(settingsAccountCacheService));
        given(settingsAccountCacheService.getCacheTtlByAccountId(anyString(), any()))
                .willReturn(Future.succeededFuture(CacheTtl.of(100, 100)));

        // when
        final Future<CacheTtl> cacheTtlResult = compositeAccountCacheService.getCacheTtlByAccountId("publisherId", timeout);

        // then
        assertThat(cacheTtlResult.succeeded()).isTrue();
        assertThat(cacheTtlResult.result()).isEqualTo(CacheTtl.of(100, 100));
    }
}
