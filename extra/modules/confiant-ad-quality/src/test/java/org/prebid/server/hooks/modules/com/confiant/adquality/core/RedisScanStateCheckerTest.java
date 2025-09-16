package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class RedisScanStateCheckerTest {

    @Mock
    private BidsScanner bidsScanner;

    private RedisScanStateChecker scanStateChecker;

    @BeforeEach
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
        verify(bidsScanner).disableScan();
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
        verify(bidsScanner).disableScan();
        Thread.sleep(1100L);
        verify(bidsScanner).enableScan();
    }
}
