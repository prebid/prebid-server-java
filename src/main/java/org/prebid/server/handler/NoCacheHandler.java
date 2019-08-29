package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.util.HttpUtil;

public class NoCacheHandler implements Handler<RoutingContext> {

    public static NoCacheHandler create() {
        return new NoCacheHandler();
    }

    @Override
    public void handle(RoutingContext context) {
        context.response()
                .putHeader(HttpUtil.CACHE_CONTROL_HEADER, "no-cache, no-store, must-revalidate")
                .putHeader(HttpUtil.PRAGMA_HEADER, "no-cache")
                .putHeader(HttpUtil.EXPIRES_HEADER, "0");
        context.next();
    }
}
