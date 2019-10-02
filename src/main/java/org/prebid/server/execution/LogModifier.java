package org.prebid.server.execution;

import io.vertx.core.logging.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class LogModifier {

    private AtomicInteger errorLevelCount;
    private volatile BiConsumer<Logger, String> defaultLogModifier;
    private volatile BiConsumer<Logger, String> logModifier;

    public LogModifier(BiConsumer<Logger, String> defaultLogModifier) {
        this.defaultLogModifier = defaultLogModifier;
    }

    public void setLogModifier(int requestCount, BiConsumer<Logger, String> logModifier) {
        this.errorLevelCount = new AtomicInteger(requestCount);
        this.logModifier = logModifier;
    }

    public BiConsumer<Logger, String> getLogModifier() {
        if (errorLevelCount != null && errorLevelCount.get() > 0) {
            errorLevelCount.decrementAndGet();
            return logModifier;
        }
        return defaultLogModifier;
    }
}

