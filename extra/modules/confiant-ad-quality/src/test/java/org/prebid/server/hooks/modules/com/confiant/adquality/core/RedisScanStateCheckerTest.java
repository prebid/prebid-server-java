package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class RedisScanStateCheckerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RedisClient redisClient;

    private RedisScanStateChecker scanStateChecker;

    @Before
    public void setUp() {
        scanStateChecker = new RedisScanStateChecker(redisClient, 1000L, Vertx.vertx());
    }

    @Test
    public void shouldHaveValidInitialScanDisabled() {
        // given

        // when

        // then
        assertTrue(scanStateChecker.isScanDisabled());
    }

    @Test
    public void shouldHaveValidScanDisabledWhenRedisClientReturnsTrue() {
        // given
        doReturn(Future.succeededFuture(true)).when(redisClient).isScanDisabled();

        // when
        scanStateChecker.run();

        // then
        assertTrue(scanStateChecker.isScanDisabled());
    }

    @Test
    public void shouldHaveValidScanDisabledWhenRedisClientReturnsFalse() throws InterruptedException {
        // given
        doReturn(Future.succeededFuture(false)).when(redisClient).isScanDisabled();

        // when
        scanStateChecker.run();
        Thread.sleep(1100L);

        // then
        assertFalse(scanStateChecker.isScanDisabled());
    }

    @Test
    public void shouldProperlyInitCheckerTimerAndCallRedisClient() throws InterruptedException {
        // given
        doReturn(Future.succeededFuture(false)).when(redisClient).isScanDisabled();

        // when
        scanStateChecker.run();

        // then
        Thread.sleep(1100L);
        verify(redisClient, times(2)).isScanDisabled();
        Thread.sleep(1100L);
        verify(redisClient, times(3)).isScanDisabled();
    }

    @Test
    public void shouldProperlyManageDisabledFlagByTimer() throws InterruptedException {
        // given
        doReturn(Future.succeededFuture(true), Future.succeededFuture(false))
                .when(redisClient).isScanDisabled();

        // when
        scanStateChecker.run();

        // then
        assertTrue(scanStateChecker.isScanDisabled());
        Thread.sleep(1100L);
        assertFalse(scanStateChecker.isScanDisabled());
    }
}
