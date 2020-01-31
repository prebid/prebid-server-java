package org.prebid.server.log;

import io.vertx.core.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ConditionalLogger {
    private ConcurrentHashMap<String, AtomicInteger> messageToCount;
    private final Logger logger;

    public ConditionalLogger(Logger logger) {
        this.logger = logger;
        this.messageToCount = new ConcurrentHashMap<>();
    }

    public void info(String message, Integer maxValue) {
        log(message, maxValue, logger -> logger.info(message));
    }

    public void error(String message, Integer maxValue) {
        log(message, maxValue, logger -> logger.error(message));
    }

    public void debug(String message, Integer maxValue) {
        log(message, maxValue, logger -> logger.debug(message));
    }

    public void warn(String message, Integer maxValue) {
        log(message, maxValue, logger -> logger.warn(message));
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
}
