package org.prebid.server.vertx.verticles.server.application;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;

import java.util.List;

public interface ApplicationResource extends Handler<RoutingContext> {

    List<HttpEndpoint> endpoints();
}
