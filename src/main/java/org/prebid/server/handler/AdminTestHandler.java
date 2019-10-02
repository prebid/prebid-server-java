package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.execution.LoggerLevelModifier;

public class AdminTestHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AdminTestHandler.class);
    private final LoggerLevelModifier errorLoggerLevelSwitch;

    public AdminTestHandler(LoggerLevelModifier errorLoggerLevelSwitch) {
        this.errorLoggerLevelSwitch = errorLoggerLevelSwitch;
    }

    @Override
    public void handle(RoutingContext context) {
        logger.info("This is an INFO level message");
        final String logMessage = String.format("This is an INFO level message");

        if (errorLoggerLevelSwitch.isLogLevelError()) {
            logger.error("Invalid request formatasdsadsad: {0}", logMessage);
        } else {
            logger.info("Invalid request format: {0}", logMessage);
        }
        logger.error("ERROR my custom \n\n");
        context.response().end("OK");
    }
}
