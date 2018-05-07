package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

public class StatusHandler implements Handler<RoutingContext> {

    private final String statusResponse;

    public StatusHandler(String statusResponse) {
        this.statusResponse = statusResponse;
    }

    @Override
    public void handle(RoutingContext context) {
        // Today, the app always considers itself ready to serve requests.
        if (StringUtils.isEmpty(statusResponse)) {
            context.response().setStatusCode(HttpResponseStatus.NO_CONTENT.code()).end();
        } else {
            context.response().end(statusResponse);
        }
    }
}
