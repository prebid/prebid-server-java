package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class SettingsCacheNotificationHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(SettingsCacheNotificationHandler.class);

    @Override
    public void handle(RoutingContext event) {
        logger.error("Invoked cache notification handler");
    }
}
