package org.prebid.server.log;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;

import java.util.function.BiConsumer;

public class CriteriaManager {

    private static final long MAX_CRITERIA_DURATION = 300000L;

    private final CriteriaLogManager criteriaLogManager;
    private final Vertx vertx;

    public CriteriaManager(CriteriaLogManager criteriaLogManager, Vertx vertx) {
        this.criteriaLogManager = criteriaLogManager;
        this.vertx = vertx;
    }

    public void addCriteria(String accountId,
                            String bidderCode,
                            String loggerLevel,
                            Integer durationMillis) {

        final Criteria criteria = Criteria.create(accountId, bidderCode, resolveLogLevel(loggerLevel));
        criteriaLogManager.addCriteria(criteria);
        vertx.setTimer(limitDuration(durationMillis), ignored -> criteriaLogManager.removeCriteria(criteria));
    }

    public void stop() {
        criteriaLogManager.removeAllCriteria();
    }

    private long limitDuration(long durationMillis) {
        return Math.min(durationMillis, MAX_CRITERIA_DURATION);
    }

    private BiConsumer<Logger, Object> resolveLogLevel(String rawLogLevel) {
        try {
            return switch (LogLevel.valueOf(rawLogLevel.toLowerCase())) {
                case info -> Logger::info;
                case warn -> Logger::warn;
                case trace -> Logger::trace;
                case error -> Logger::error;
                case fatal -> Logger::fatal;
                case debug -> Logger::debug;
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid LoggingLevel: " + rawLogLevel);
        }
    }

    private enum LogLevel {
        info, warn, trace, error, fatal, debug
    }
}
