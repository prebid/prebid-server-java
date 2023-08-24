package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.Vertx;

public class RedisScanStateChecker {

    private final BidsScanner bidsScanner;

    private final long scanStateCheckInterval;

    private final Vertx vertx;

    public RedisScanStateChecker(
            BidsScanner bidsScanner,
            long scanStateCheckInterval,
            Vertx vertx) {
        this.bidsScanner = bidsScanner;
        this.scanStateCheckInterval = scanStateCheckInterval;
        this.vertx = vertx;
    }

    public void run() {
        verifyScanFlag();
        vertx.setPeriodic(scanStateCheckInterval, ignored -> verifyScanFlag());
    }

    private void verifyScanFlag() {
        bidsScanner.isScanDisabledFlag().onComplete(result -> {
            final boolean isScanDisabled = result.result();
            if (isScanDisabled) {
                bidsScanner.disableScan();
            } else {
                bidsScanner.enableScan();
            }
        });
    }
}
