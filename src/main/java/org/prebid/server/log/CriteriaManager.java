package org.prebid.server.log;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.deals.model.LogCriteriaFilter;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class CriteriaManager {

    private static final long MAX_CRITERIA_DURATION = 300000L;

    private static final Logger logger = LoggerFactory.getLogger(CriteriaManager.class);

    private final CriteriaLogManager criteriaLogManager;
    private final Vertx vertx;

    public CriteriaManager(CriteriaLogManager criteriaLogManager, Vertx vertx) {
        this.criteriaLogManager = criteriaLogManager;
        this.vertx = vertx;
    }

    public void addCriteria(String accountId, String bidderCode, String lineItemId, String loggerLevel,
                            Integer durationMillis) {
        final Criteria criteria = Criteria.create(accountId, bidderCode, lineItemId, resolveLogLevel(loggerLevel));
        criteriaLogManager.addCriteria(criteria);
        vertx.setTimer(limitDuration(durationMillis), ignored -> criteriaLogManager.removeCriteria(criteria));
    }

    public void addCriteria(LogCriteriaFilter filter, Long durationSeconds) {
        if (filter != null) {
            final Criteria criteria = Criteria.create(filter.getAccountId(), filter.getBidderCode(),
                    filter.getLineItemId(), Logger::error);
            criteriaLogManager.addCriteria(criteria);
            logger.info("Logger was updated with new criteria {0}", criteria);
            vertx.setTimer(limitDuration(TimeUnit.SECONDS.toMillis(durationSeconds)),
                    ignored -> criteriaLogManager.removeCriteria(criteria));
        }
    }

    public void stop() {
        criteriaLogManager.removeAllCriteria();
    }

    private long limitDuration(long durationMillis) {
        return Math.min(durationMillis, MAX_CRITERIA_DURATION);
    }

    private BiConsumer<Logger, Object> resolveLogLevel(String rawLogLevel) {
        final LogLevel logLevel;
        try {
            logLevel = LogLevel.valueOf(rawLogLevel.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Invalid LoggingLevel: %s", rawLogLevel));
        }

        switch (logLevel) {
            case info:
                return Logger::info;
            case warn:
                return Logger::warn;
            case trace:
                return Logger::trace;
            case error:
                return Logger::error;
            case fatal:
                return Logger::fatal;
            case debug:
                return Logger::debug;
            default:
                throw new IllegalArgumentException(String.format("Unknown LoggingLevel: %s", logLevel));
        }
    }

    private enum LogLevel {
        info, warn, trace, error, fatal, debug
    }
}
