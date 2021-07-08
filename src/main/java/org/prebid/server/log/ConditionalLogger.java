package org.prebid.server.log;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.logging.Logger;
import org.apache.commons.lang3.ObjectUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ConditionalLogger {

    private static final int CACHE_MAXIMUM_SIZE = 10_000;
    private static final int EXPIRE_CACHE_DURATION = 1;

    private final String key;
    private final Logger logger;

    private final ConcurrentMap<String, AtomicInteger> messageToCount;
    private final ConcurrentMap<String, Long> messageToWait;

    public ConditionalLogger(String key, Logger logger) {
        this.key = key; // can be null
        this.logger = Objects.requireNonNull(logger);

        messageToCount = Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(EXPIRE_CACHE_DURATION, TimeUnit.HOURS)
                .<String, AtomicInteger>build()
                .asMap();

        messageToWait = Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(EXPIRE_CACHE_DURATION, TimeUnit.HOURS)
                .<String, Long>build()
                .asMap();
    }

    public ConditionalLogger(Logger logger) {
        this(null, logger);
    }

    public void infoWithKey(String key, String message, int limit) {
        log(key, limit, logger -> logger.info(message));
    }

    public void info(String message, int limit) {
        log(message, limit, logger -> logger.info(message));
    }

    public void info(String message, long duration, TimeUnit unit) {
        log(message, duration, unit, logger -> logger.info(message));
    }

    public void info(String message, double samplingRate) {
        if (samplingRate >= 1.0d || ThreadLocalRandom.current().nextDouble() < samplingRate) {
            logger.warn(message);
        }
    }

    public void errorWithKey(String key, String message, int limit) {
        log(key, limit, logger -> logger.error(message));
    }

    public void error(String message, int limit) {
        log(message, limit, logger -> logger.error(message));
    }

    public void error(String message, long duration, TimeUnit unit) {
        log(message, duration, unit, logger -> logger.error(message));
    }

    public void error(String message, double samplingRate) {
        if (samplingRate >= 1.0d || ThreadLocalRandom.current().nextDouble() < samplingRate) {
            logger.error(message);
        }
    }

    public void debug(String message, int limit) {
        log(message, limit, logger -> logger.debug(message));
    }

    public void debug(String message, long duration, TimeUnit unit) {
        log(message, duration, unit, logger -> logger.debug(message));
    }

    public void warn(String message, int limit) {
        log(message, limit, logger -> logger.warn(message));
    }

    public void warn(String message, long duration, TimeUnit unit) {
        log(message, duration, unit, logger -> logger.warn(message));
    }

    public void warn(String message, double samplingRate) {
        if (samplingRate >= 1.0d || ThreadLocalRandom.current().nextDouble() < samplingRate) {
            logger.warn(message);
        }
    }

    /**
     * Calls {@link Consumer} if the given limit for specified key is not exceeded.
     */
    private void log(String key, int limit, Consumer<Logger> consumer) {
        final String resolvedKey = ObjectUtils.defaultIfNull(this.key, key);
        final AtomicInteger count = messageToCount.computeIfAbsent(resolvedKey, ignored -> new AtomicInteger());
        if (count.incrementAndGet() >= limit) {
            count.set(0);
            consumer.accept(logger);
        }
    }

    /**
     * Calls {@link Consumer} if the given time for specified key is not exceeded.
     */
    private void log(String key, long duration, TimeUnit unit, Consumer<Logger> consumer) {
        final long currentTime = Instant.now().toEpochMilli();
        final String resolvedKey = ObjectUtils.defaultIfNull(this.key, key);
        final long endTime = messageToWait.computeIfAbsent(resolvedKey, ignored -> calculateEndTime(duration, unit));

        if (currentTime >= endTime) {
            messageToWait.replace(resolvedKey, endTime, calculateEndTime(duration, unit));
            consumer.accept(logger);
        }
    }

    /**
     * Returns time in millis as current time incremented by specified duration.
     */
    private static long calculateEndTime(long duration, TimeUnit unit) {
        final long durationInMillis = unit.toMillis(duration);
        return Instant.now().plusMillis(durationInMillis).toEpochMilli();
    }
}
