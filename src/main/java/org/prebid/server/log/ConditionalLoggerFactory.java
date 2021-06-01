package org.prebid.server.log;

import io.vertx.core.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class ConditionalLoggerFactory {

    private static ConcurrentHashMap<Logger, ConditionalLogger> loggers = new ConcurrentHashMap<>();

    private ConditionalLoggerFactory() {
    }

    public static ConditionalLogger getOrCreate(Logger logger) {
        return loggers.computeIfAbsent(logger, ConditionalLogger::new);
    }
}
