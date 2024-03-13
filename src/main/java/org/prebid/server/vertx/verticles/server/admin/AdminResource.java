package org.prebid.server.vertx.verticles.server.admin;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public interface AdminResource extends Handler<RoutingContext> {

    String path();

    boolean isOnApplicationPort();

    boolean isSecured();
}
