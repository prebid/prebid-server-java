package org.prebid.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.prebid.server.handler.SettingsCacheNotificationHandler;

public class CacheNotificationVerticle extends AbstractVerticle {

    private final Vertx vertx;
    private final Integer port;
    private final SettingsCacheNotificationHandler cacheNotificationHandler;
    private final SettingsCacheNotificationHandler ampCacheNotificationHandler;
    private final BodyHandler bodyHandler;

    public CacheNotificationVerticle(Vertx vertx, Integer port,
                              SettingsCacheNotificationHandler cacheNotificationHandler,
                              SettingsCacheNotificationHandler ampCacheNotificationHandler,
                              BodyHandler bodyHandler) {
        this.vertx = vertx;
        this.port = port;
        this.cacheNotificationHandler = cacheNotificationHandler;
        this.ampCacheNotificationHandler = ampCacheNotificationHandler;
        this.bodyHandler = bodyHandler;
    }

    @Override
    public void start(Future<Void> startFuture) {
        final Router router = Router.router(vertx);
        router.route().handler(bodyHandler);
        router.route("/storedrequests/openrtb2").handler(cacheNotificationHandler);
        router.route("/storedrequests/amp").handler(ampCacheNotificationHandler);

        final Future<HttpServer> httpServerFuture = Future.future();
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(port, httpServerFuture);

        httpServerFuture.compose(httpServer -> startFuture.complete(), startFuture);
    }
}
