package org.prebid.server.log;

import io.vertx.core.logging.Logger;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ConditionalLogger {
    private ConcurrentHashMap<String, AtomicInteger> messageToCount;
    private ConcurrentHashMap<String, AtomicLong> messageToWait;
    private final Logger logger;

    public ConditionalLogger(Logger logger) {
        this.logger = logger;
        this.messageToCount = new ConcurrentHashMap<>();
        this.messageToWait = new ConcurrentHashMap<>();
    }

    public void info(String message, Integer maxValue) {
        log(message, maxValue, logger -> logger.info(message));
    }

    public void info(String message, long amount, TimeUnit unit) {
        log(message, amount, unit, logger -> logger.info(message));
    }

    public void error(String message, Integer maxValue) {
        log(message, maxValue, logger -> logger.error(message));
    }

    public void error(String message, long amount, TimeUnit unit) {
        log(message, amount, unit, logger -> logger.error(message));
    }

    public void debug(String message, Integer maxValue) {
        log(message, maxValue, logger -> logger.debug(message));
    }

    public void debug(String message, long amount, TimeUnit unit) {
        log(message, amount, unit, logger -> logger.debug(message));
    }

    public void warn(String message, Integer maxValue) {
        log(message, maxValue, logger -> logger.warn(message));
    }

    public void warn(String message, long amount, TimeUnit unit) {
        log(message, amount, unit, logger -> logger.warn(message));
    }

    public void log(String key, Integer maxValue, Consumer<Logger> onLog) {
        AtomicInteger currentValue = messageToCount.compute(
                key, (k, currentCounter) -> currentCounter != null ? currentCounter : new AtomicInteger(0)
        );
        if (currentValue.incrementAndGet() >= maxValue) {
            currentValue.set(0);
            onLog.accept(logger);
        }
    }

    public void log(String key, long amount, TimeUnit unit, Consumer<Logger> onLog) {
        messageToWait.compute(key, (k, result) -> {
            if (result == null) {
                result = recalculateDate(amount, unit);
            }
            if (currentTimeMillis() >= result.get()) {
                result = recalculateDate(amount, unit);
                onLog.accept(logger);
            }
            return result;
        });
    }

    private AtomicLong recalculateDate(long amount, TimeUnit unit) {
        final long amountInMillis = unit.toMillis(amount);
        final Date result = new Date(currentTimeMillis() + amountInMillis);
        return new AtomicLong(result.getTime());
    }

    private long currentTimeMillis() {
        return new Date().getTime();
    }

}
