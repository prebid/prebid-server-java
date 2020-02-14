package org.prebid.server.log;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.logging.Logger;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ConditionalLogger {

    private static final int EXPIRE_CACHE_DURATION = 1;
    private static final int CACHE_MAXIMUM_SIZE = 10_000;

    private ConcurrentMap<String, AtomicInteger> messageToCount;
    private ConcurrentMap<String, Long> messageToWait;

    private final Logger logger;

    public ConditionalLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger);
        messageToWait = Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(EXPIRE_CACHE_DURATION, TimeUnit.HOURS)
                .<String, Long>build()
                .asMap();
        messageToCount = Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(EXPIRE_CACHE_DURATION, TimeUnit.HOURS)
                .<String, AtomicInteger>build()
                .asMap();
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

    private void log(String key, Integer maxValue, Consumer<Logger> consumer) {
        final AtomicInteger value = messageToCount.computeIfAbsent(key, ignored -> new AtomicInteger());
        if (value.incrementAndGet() >= maxValue) {
            value.set(0);
            consumer.accept(logger);
        }
    }

    private void log(String key, long amount, TimeUnit unit, Consumer<Logger> consumer) {
        final long currentTime = Instant.now().toEpochMilli();
        final Long value = messageToWait.computeIfAbsent(key, ignored -> recalculateDate(amount, unit));

        if (currentTime >= value) {
            messageToWait.replace(key, value, recalculateDate(amount, unit));
            consumer.accept(logger);
        }
    }

    private static long recalculateDate(long amount, TimeUnit unit) {
        final long amountInMillis = unit.toMillis(amount);
        final Instant resultInstant = Instant.now().plusMillis(amountInMillis);
        return resultInstant.toEpochMilli();
    }

}
