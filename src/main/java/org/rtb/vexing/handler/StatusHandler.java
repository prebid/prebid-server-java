package org.rtb.vexing.handler;

import io.vertx.ext.web.RoutingContext;

public class StatusHandler {

    public void status(RoutingContext context) {
        // just respond with HTTP 200 OK
        context.response().end();
    }
}
