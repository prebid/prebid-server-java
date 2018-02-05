package org.rtb.vexing;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.rtb.vexing.json.ObjectMapperConfigurer;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.settings.StoredRequestFetcher;

public class PrebidVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PrebidVerticle.class);

    private final Integer port;
    private final Vertx vertx;
    private final Router router;
    private final ApplicationSettings applicationSettings;
    private final StoredRequestFetcher storedRequestFetcher;

    public PrebidVerticle(Vertx vertx, Router router, ApplicationSettings applicationSettings,
                          StoredRequestFetcher storedRequestFetcher, Integer port) {
        this.vertx = vertx;
        this.router = router;
        this.applicationSettings = applicationSettings;
        this.storedRequestFetcher = storedRequestFetcher;
        this.port = port;
    }

    /**
     * Start the verticle instance.
     */
    @Override
    public void start(Future<Void> startFuture) {
        ObjectMapperConfigurer.configure();

        applicationSettings.initialize()
                .compose(ignored -> storedRequestFetcher.initialize())
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
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(port, httpServerFuture);

        return httpServerFuture;
    }
}
