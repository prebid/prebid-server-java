package org.prebid.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;

public class PrebidVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PrebidVerticle.class);

    private final Integer port;
    private final Vertx vertx;
    private final Router router;

    public PrebidVerticle(Vertx vertx, Router router, Integer port) {
        this.vertx = vertx;
        this.router = router;
        this.port = port;
    }

    /**
     * Start the verticle instance.
     */
    @Override
    public void start(Future<Void> startFuture) {
        startHttpServer().compose(httpServer -> startFuture.complete(), startFuture);
    }

    private Future<HttpServer> startHttpServer() {
        final Future<HttpServer> httpServerFuture = Future.future();

        final HttpServerOptions httpServerOptions = new HttpServerOptions()
                .setHandle100ContinueAutomatically(true)
                .setCompressionSupported(true);

        vertx.createHttpServer(
                httpServerOptions)
                .requestHandler(router::accept)
                .listen(port, httpServerFuture);

        return httpServerFuture;
    }
}
