package org.prebid.server.execution;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class LoggerLevelModifier {

    private AtomicInteger errorLevelCount;
    private volatile Consumer<Logger> loggerModifier;

    public void setErrorOnBadRequestCount(int errorLevelCount, Consumer<Logger> loggerModifier) {
        this.errorLevelCount = new AtomicInteger(errorLevelCount);
        this.loggerModifier = loggerModifier;
    }

    public boolean log() {
        if (errorLevelCount != null && errorLevelCount.get() > 0) {
            errorLevelCount.decrementAndGet();
            return true;
        }
        return false;
    }
}

