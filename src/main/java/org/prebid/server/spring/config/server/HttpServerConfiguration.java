package org.prebid.server.spring.config.server;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.prebid.server.handler.ExceptionHandler;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@ConditionalOnProperty(name = "server.http.enabled", havingValue = "true")
public class HttpServerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(HttpServerConfiguration.class);

    @Autowired
    private ContextRunner contextRunner;

    @Autowired
    private Vertx vertx;

    @Autowired
    private HttpServerOptions httpServerOptions;

    @Autowired
    private ExceptionHandler exceptionHandler;

    @Autowired
    @Qualifier("router")
    private Router router;

    @Value("#{'${http.port:${server.http.port}}'}")
    private Integer httpPort;

    // TODO: remove support for properties with http prefix after transition period
    @Value("#{'${vertx.http-server-instances:${server.http.server-instances}}'}")
    private Integer httpServerNum;

    @PostConstruct
    public void startHttpServer() {
        logger.info(
                "Starting {0} instances of Http Server to serve requests on port {1,number,#}",
                httpServerNum,
                httpPort);

        contextRunner.<HttpServer>runOnNewContext(httpServerNum, promise ->
                vertx.createHttpServer(httpServerOptions)
                        .exceptionHandler(exceptionHandler)
                        .requestHandler(router)
                        .listen(httpPort, promise));

        logger.info("Successfully started {0} instances of Http Server", httpServerNum);
    }
}
