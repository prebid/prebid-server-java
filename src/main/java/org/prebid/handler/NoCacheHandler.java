package org.prebid.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

public class NoCacheHandler implements Handler<RoutingContext> {

    private static final CharSequence PRAGMA = HttpHeaders.createOptimized("Pragma");

    public static NoCacheHandler create() {
        return new NoCacheHandler();
    }

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.response()
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .putHeader(PRAGMA, "no-cache")
                .putHeader(HttpHeaders.EXPIRES, "0");
        routingContext.next();
    }
}
