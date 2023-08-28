package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class RedisScanStateCheckerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidsScanner bidsScanner;

    private RedisScanStateChecker scanStateChecker;

    @Before
    public void setUp() {
        scanStateChecker = new RedisScanStateChecker(bidsScanner, 1000L, Vertx.vertx());
    }

    @Test
    public void shouldDisableScanWhenRedisClientReturnsTrueFlag() {
        // given
        doReturn(Future.succeededFuture(true)).when(bidsScanner).isScanDisabledFlag();

        // when
        scanStateChecker.run();

        // then
        verify(bidsScanner, times(1)).disableScan();
    }

    @Test
    public void shouldEnableScanWhenRedisClientReturnsFalseFlag() throws InterruptedException {
        // given
        doReturn(Future.succeededFuture(false)).when(bidsScanner).isScanDisabledFlag();

        // when
        scanStateChecker.run();
        Thread.sleep(1100L);

        // then
        verify(bidsScanner, times(2)).enableScan();
    }

    @Test
    public void shouldProperlyInitCheckerTimerAndCallBidsScanner() throws InterruptedException {
        // given
        doReturn(Future.succeededFuture(false)).when(bidsScanner).isScanDisabledFlag();

        // when
        scanStateChecker.run();

        // then
        Thread.sleep(1100L);
        verify(bidsScanner, times(2)).isScanDisabledFlag();
        Thread.sleep(1100L);
        verify(bidsScanner, times(3)).isScanDisabledFlag();
    }

    @Test
    public void shouldProperlyManageDisabledFlagByTimer() throws InterruptedException {
        // given
        doReturn(Future.succeededFuture(true), Future.succeededFuture(false))
                .when(bidsScanner).isScanDisabledFlag();

        // when
        scanStateChecker.run();

        // then
        verify(bidsScanner, times(1)).disableScan();
        Thread.sleep(1100L);
        verify(bidsScanner, times(1)).enableScan();
    }
}
