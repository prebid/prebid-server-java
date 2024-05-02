package org.prebid.server.vertx.verticles.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.handler.ExceptionHandler;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.Objects;

public class ServerVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(ServerVerticle.class);

    private final String name;
    private final HttpServerOptions serverOptions;
    private final SocketAddress address;
    private final Router router;
    private final ExceptionHandler exceptionHandler;

    public ServerVerticle(String name,
                          HttpServerOptions serverOptions,
                          SocketAddress address,
                          Router router,
                          ExceptionHandler exceptionHandler) {

        this.name = Objects.requireNonNull(name);
        this.serverOptions = Objects.requireNonNull(serverOptions);
        this.address = Objects.requireNonNull(address);
        this.router = Objects.requireNonNull(router);
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
    }

    public ServerVerticle(String name, SocketAddress address, Router router) {
        this.name = Objects.requireNonNull(name);
        this.serverOptions = null;
        this.address = Objects.requireNonNull(address);
        this.router = Objects.requireNonNull(router);
        this.exceptionHandler = null;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        final HttpServerOptions httpServerOptions = ObjectUtils.getIfNull(serverOptions, HttpServerOptions::new);
        final HttpServer server = vertx.createHttpServer(httpServerOptions)
                .requestHandler(router);

        if (exceptionHandler != null) {
            server.exceptionHandler(exceptionHandler);
        }

        server.listen(address, result -> onServerStarted(result, startPromise));
    }

    private void onServerStarted(AsyncResult<HttpServer> result, Promise<Void> startPromise) {
        if (result.succeeded()) {
            startPromise.tryComplete();
            logger.info(
                    "Successfully started {} instance on address: {}, thread: {}",
                    name,
                    address,
                    Thread.currentThread().getName());
        } else {
            startPromise.tryFail(result.cause());
        }
    }
}
