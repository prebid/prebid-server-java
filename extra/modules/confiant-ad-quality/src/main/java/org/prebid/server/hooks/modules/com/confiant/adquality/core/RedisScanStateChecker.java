package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.Vertx;

public class RedisScanStateChecker {

    private volatile Boolean isScanDisabled = true;

    private final RedisClient redisClient;

    private final long timerDelay;

    private final Vertx vertx;

    public RedisScanStateChecker(
            RedisClient redisClient,
            long timerDelay,
            Vertx vertx) {
        this.redisClient = redisClient;
        this.timerDelay = timerDelay;
        this.vertx = vertx;
    }

    public void run() {
        verifyScanFlag();
        vertx.setPeriodic(timerDelay, ignored -> verifyScanFlag());
    }

    public boolean isScanDisabled() {
        return isScanDisabled;
    }

    private void verifyScanFlag() {
        redisClient.isScanDisabled().onComplete(result -> {
            isScanDisabled = result.result();
        });
    }
}
