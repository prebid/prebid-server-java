package org.prebid.server.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.spi.ExtendedLogger;

public class LoggerFactory {

    private LoggerFactory() {
    }

    public static Logger getLogger(Class<?> clazz) {
        final String name = clazz.isAnonymousClass()
                ? clazz.getEnclosingClass().getCanonicalName()
                : clazz.getCanonicalName();

        return getLogger(name);
    }

    public static Logger getLogger(String name) {
        return new Logger((ExtendedLogger) LogManager.getLogger(name));
    }
}
