package org.prebid.server.log;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.ObjectUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ConditionalLogger {

    private static final int CACHE_MAXIMUM_SIZE = 10_000;
    private static final int EXPIRE_CACHE_DURATION = 1;

    private final String key;
    private final Logger logger;

    private final ConcurrentMap<String, Instant> messageToWait;

    public ConditionalLogger(String key, Logger logger) {
        this.key = key; // can be null
        this.logger = Objects.requireNonNull(logger);

        messageToWait = Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(EXPIRE_CACHE_DURATION, TimeUnit.HOURS)
                .<String, Instant>build()
                .asMap();
    }

    public ConditionalLogger(Logger logger) {
        this(null, logger);
    }

    public void debug(String message, long duration, TimeUnit unit) {
        log(message, duration, unit, logger::debug);
    }

    public void debug(String message, double samplingRate) {
        log(message, samplingRate, logger::debug);
    }

    public void info(String message, long duration, TimeUnit unit) {
        log(message, duration, unit, logger::info);
    }

    public void info(String message, double samplingRate) {
        log(message, samplingRate, logger::info);
    }

    public void warn(String message, long duration, TimeUnit unit) {
        log(message, duration, unit, logger::warn);
    }

    public void warn(String message, double samplingRate) {
        log(message, samplingRate, logger::warn);
    }

    public void error(String message, long duration, TimeUnit unit) {
        log(message, duration, unit, logger::error);
    }

    public void error(String message, double samplingRate) {
        log(message, samplingRate, logger::error);
    }

    private static void log(String message, double samplingRate, Consumer<String> logger) {
        if (samplingRate >= 1.0d || ThreadLocalRandom.current().nextDouble() < samplingRate) {
            logger.accept(message);
        }
    }

    private void log(String message, long duration, TimeUnit unit, Consumer<String> logger) {
        final String key = ObjectUtils.defaultIfNull(this.key, message);
        final Instant currentTime = Instant.now();
        final Instant endTime = messageToWait.computeIfAbsent(
                key, ignored -> calculateEndTime(currentTime, duration, unit));

        // we skip 1st ever log event for the key
        if (currentTime.isAfter(endTime) || currentTime.equals(endTime)) {
            messageToWait.replace(key, endTime, calculateEndTime(currentTime, duration, unit));
            logger.accept(message);
        }
    }

    private static Instant calculateEndTime(Instant currentTime, long duration, TimeUnit unit) {
        return currentTime.plusMillis(unit.toMillis(duration));
    }
}
