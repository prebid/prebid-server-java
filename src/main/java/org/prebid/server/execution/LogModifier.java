package org.prebid.server.execution;

import io.vertx.core.logging.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class LogModifier {

    private AtomicInteger errorLevelCount;
    private volatile BiConsumer<Logger, String> defaultLogModifier;
    private volatile BiConsumer<Logger, String> logModifier;

    public LogModifier(Logger defaultLogger) {
        defaultLogModifier = defaultLogModifier(defaultLogger);
    }

    private static BiConsumer<Logger, String> defaultLogModifier(Logger defaultLogger) {
        if (defaultLogger.isTraceEnabled()) {
            return Logger::trace;
        } else if (defaultLogger.isDebugEnabled()) {
            return Logger::debug;
        } else if (defaultLogger.isInfoEnabled()) {
            return Logger::info;
        } else if (defaultLogger.isWarnEnabled()) {
            return Logger::warn;
        }
        return Logger::error;
    }

    public void set(BiConsumer<Logger, String> logModifier, int requestCount) {
        this.errorLevelCount = new AtomicInteger(requestCount);
        this.logModifier = Objects.requireNonNull(logModifier);
    }

    public BiConsumer<Logger, String> get() {
        if (errorLevelCount != null && errorLevelCount.get() > 0) {
            errorLevelCount.decrementAndGet();
            return logModifier;
        }
        return defaultLogModifier;
    }
}

