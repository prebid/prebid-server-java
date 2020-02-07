package org.prebid.server.log;

import io.vertx.core.logging.Logger;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ConditionalLogger {
    private ConcurrentHashMap<String, AtomicInteger> messageToCount;
    private ConcurrentHashMap<String, Long> messageToWait;

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

    public void log(String key, Integer maxValue, Consumer<Logger> consumer) {
        final AtomicInteger currentValue = messageToCount.compute(
                key, (currentKey, currentCounter) -> currentCounter != null ? currentCounter : new AtomicInteger(0)
        );
        if (currentValue.incrementAndGet() >= maxValue) {
            currentValue.set(0);
            consumer.accept(logger);
        }
    }

    public void log(String key, long amount, TimeUnit unit, Consumer<Logger> consumer) {
        messageToWait.compute(key, (currentKey, lastTimeMillis) -> {
            if (lastTimeMillis == null || currentTimeMillis() >= lastTimeMillis) {
                lastTimeMillis = recalculateDate(amount, unit);
                consumer.accept(logger);
            }
            return lastTimeMillis;
        });
    }

    private long recalculateDate(long amount, TimeUnit unit) {
        final long amountInMillis = unit.toMillis(amount);
        Instant resultInstant = Instant.now().plusMillis(amountInMillis);
        return resultInstant.toEpochMilli();
    }

    private long currentTimeMillis() {
        return new Date().getTime();
    }

}
