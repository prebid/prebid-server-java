package org.prebid.server.vertx.verticles.server;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.handler.ExceptionHandler;
import org.prebid.server.vertx.verticles.InitializableVerticle;

import java.util.Objects;
import java.util.function.Supplier;

public class ServerVerticle extends InitializableVerticle {

    private static final Logger logger = LoggerFactory.getLogger(ServerVerticle.class);

    private final String name;
    private final HttpServerOptions serverOptions;
    private final SocketAddress address;
    private final Router router;
    private final ExceptionHandler exceptionHandler;

    public ServerVerticle(String name,
                          HttpServerOptions serverOptions,
                          SocketAddress address,
                          Supplier<Router> routerFactory,
                          ExceptionHandler exceptionHandler) {

        this.name = Objects.requireNonNull(name);
        this.serverOptions = Objects.requireNonNull(serverOptions);
        this.address = Objects.requireNonNull(address);
        this.router = Objects.requireNonNull(routerFactory.get());
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
    }

    public ServerVerticle(String name, SocketAddress address, Supplier<Router> routerFactory) {
        this.name = Objects.requireNonNull(name);
        this.serverOptions = null;
        this.address = Objects.requireNonNull(address);
        this.router = Objects.requireNonNull(routerFactory.get());
        this.exceptionHandler = null;
    }

    @Override
    public void init(Vertx vertx, Context context) {
        final HttpServerOptions httpServerOptions = ObjectUtils.defaultIfNull(serverOptions, new HttpServerOptions());
        final HttpServer server = vertx.createHttpServer(httpServerOptions)
                .requestHandler(router);

        if (exceptionHandler != null) {
            server.exceptionHandler(exceptionHandler);
        }

        server.listen(address, this::onServerStarted);
    }

    private void onServerStarted(AsyncResult<HttpServer> result) {
        if (result.succeeded()) {
            signalInitializationSuccess();
            logger.info(
                    "Successfully started {0} instance on address: {1}, thread: {2}",
                    name,
                    address,
                    Thread.currentThread().getName());
        } else {
            signalInitializationFailure(result.cause());
        }
    }
}
