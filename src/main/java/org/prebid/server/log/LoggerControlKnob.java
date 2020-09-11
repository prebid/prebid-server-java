package org.prebid.server.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.vertx.core.Vertx;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LoggerControlKnob {

    private static final String PREBID_LOGGER = "org.prebid.server";

    private final Vertx vertx;
    private final Logger logger;
    private final Level originalLevel;

    private final Lock lock = new ReentrantLock();
    private Long restoreTimerId = null;

    public LoggerControlKnob(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);

        logger = getPrebidLogger();
        originalLevel = logger != null ? logger.getLevel() : null;
    }

    public void changeLogLevel(String level, Duration duration) {
        if (logger == null) {
            return;
        }

        lock.lock();
        try {
            if (restoreTimerId != null) {
                vertx.cancelTimer(restoreTimerId);
                restoreTimerId = null;
            }

            logger.setLevel(Level.toLevel(level, originalLevel));
            restoreTimerId = vertx.setTimer(duration.toMillis(), this::resetLoggerLevel);
        } finally {
            lock.unlock();
        }
    }

    private static Logger getPrebidLogger() {
        final org.slf4j.Logger prebidSlf4jLogger = LoggerFactory.getLogger(PREBID_LOGGER);
        return prebidSlf4jLogger instanceof Logger ? (Logger) prebidSlf4jLogger : null;
    }

    private void resetLoggerLevel(long triggeredTimerId) {
        lock.lock();
        try {
            if (restoreTimerId == null || triggeredTimerId != restoreTimerId) {
                return;
            }

            logger.setLevel(originalLevel);
            restoreTimerId = null;
        } finally {
            lock.unlock();
        }
    }
}
