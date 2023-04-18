package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import java.util.Timer;
import java.util.TimerTask;

public class RedisScanStateChecker {

    private boolean isScanDisabled = true;

    private final RedisClient redisClient;

    private final long timerDelay;

    public RedisScanStateChecker(
            RedisClient redisClient,
            long timerDelay
    ) {
        this.redisClient = redisClient;
        this.timerDelay = timerDelay;
    }

    public void run() {
        TimerTask checkerTask = new TimerTask() {
            public void run() {
                isScanDisabled = redisClient.isScanDisabled();
            }
        };

        Timer timer = new Timer("ScanStateChecker");
        timer.schedule(checkerTask, 0, timerDelay);
    }

    public boolean isScanDisabled() {
        return isScanDisabled;
    }
}
