package org.prebid.server.cache.account;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleAccountCacheServiceTest {

    private SimpleAccountCacheService simpleAccountCacheService;

    private Clock clock;

    private Timeout timeout;

    @Before
    public void setUp() {
        simpleAccountCacheService = new SimpleAccountCacheService(Collections.singletonMap("1001", CacheTtl.of(100, 100)));
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);
    }

    @Test
    public void shouldReturnSucceededFutureWhenAccountIdIsInMap() {
        // when
        final Future<CacheTtl> cacheTtlFuture = simpleAccountCacheService.getCacheTtlByAccountId("1001", timeout);

        // then
        assertThat(cacheTtlFuture.succeeded()).isTrue();
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.of(100, 100));
    }

    @Test
    public void shouldReturnSucceededFutureWithEmptyCacheTTLWhenAccountIdIsNotInMap() {
        // when
        final Future<CacheTtl> cacheTtlFuture = simpleAccountCacheService.getCacheTtlByAccountId("1002", timeout);

        // then
        assertThat(cacheTtlFuture.succeeded()).isTrue();
        assertThat(cacheTtlFuture.result()).isEqualTo(CacheTtl.empty());
    }

    @Test
    public void shouldReturnFailedFutureWhenTimeoutRemains() {
        timeout = new TimeoutFactory(clock).create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        // when
        final Future<CacheTtl> cacheTtlFuture = simpleAccountCacheService.getCacheTtlByAccountId("1001", timeout);

        // then
        assertThat(cacheTtlFuture.failed()).isTrue();
        assertThat(cacheTtlFuture.cause()).isInstanceOf(TimeoutException.class).hasMessage("Timeout has been exceeded");
    }
}
