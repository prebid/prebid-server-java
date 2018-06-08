package org.prebid.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.prebid.server.vertx.JdbcClient;

public class PrebidVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PrebidVerticle.class);

    private final Integer port;
    private final Vertx vertx;
    private final Router router;
    private final JdbcClient jdbcClient;

    public PrebidVerticle(Vertx vertx, Router router, JdbcClient jdbcClient, Integer port) {
        this.vertx = vertx;
        this.router = router;
        this.jdbcClient = jdbcClient;
        this.port = port;
    }

    /**
     * Start the verticle instance.
     */
    @Override
    public void start(Future<Void> startFuture) {
        final Future<Void> jdbcClientFuture = jdbcClient != null ? jdbcClient.initialize() : Future.succeededFuture();
        jdbcClientFuture
                .compose(ignored -> startHttpServer())
                .compose(
                        httpServer -> {
                            logger.debug("Prebid verticle has been started successfully");
                            startFuture.complete();
                        },
                        startFuture);
    }

    private Future<HttpServer> startHttpServer() {
        final Future<HttpServer> httpServerFuture = Future.future();
        vertx.createHttpServer(new HttpServerOptions().setHandle100ContinueAutomatically(true))
                .requestHandler(router::accept)
                .listen(port, httpServerFuture);

        return httpServerFuture;
    }
}
